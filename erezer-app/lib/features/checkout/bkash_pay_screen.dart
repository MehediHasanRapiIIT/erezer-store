import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../data/models.dart';
import '../../data/pricing.dart';

/// Pays a bKash order: bKash's own page opens inside the app, and when bKash
/// sends the customer back to the shop the app takes over, has the shop's
/// server confirm the payment with bKash, and shows how it went.
class BkashPayScreen extends ConsumerStatefulWidget {
  const BkashPayScreen({super.key, required this.order});
  final PlacedOrder order;

  @override
  ConsumerState<BkashPayScreen> createState() => _BkashPayScreenState();
}

enum _Step { starting, paying, confirming, failed, cancelled }

class _BkashPayScreenState extends ConsumerState<BkashPayScreen> {
  _Step _step = _Step.starting;
  WebViewController? _web;
  String? _message;
  bool _returned = false;
  int _loading = 0;

  @override
  void initState() {
    super.initState();
    _start();
  }

  Future<void> _start() async {
    setState(() {
      _step = _Step.starting;
      _message = null;
      _returned = false;
    });
    try {
      // An order from Track an order is known by its number only.
      final o = widget.order;
      final payment = await ref
          .read(apiProvider)
          .bkashInit(orderId: o.id.isEmpty ? null : o.id, orderNumber: o.orderNumber);
      final url = payment.bkashUrl;
      if (url == null || url.isEmpty) {
        throw ApiException(payment.errorMessage ?? 'bKash did not open. Please try again.');
      }
      final web = WebViewController()
        ..setJavaScriptMode(JavaScriptMode.unrestricted)
        ..setBackgroundColor(Colors.white)
        ..setNavigationDelegate(
          NavigationDelegate(
            onNavigationRequest: (request) {
              if (!_isReturn(request.url)) return NavigationDecision.navigate;
              _back(request.url);
              return NavigationDecision.prevent;
            },
            // Some return trips arrive as a redirect the check above doesn't see.
            onPageStarted: (url) {
              if (_isReturn(url)) _back(url);
            },
            onProgress: (p) {
              if (mounted) setState(() => _loading = p);
            },
          ),
        );
      await web.loadRequest(Uri.parse(url));
      if (!mounted) return;
      setState(() {
        _web = web;
        _step = _Step.paying;
      });
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          _step = _Step.failed;
          _message = e.message;
        });
      }
    }
  }

  /// bKash sends the customer to the shop's server (…/api/payments/bkash/callback),
  /// which forwards a website customer to the shop's result page (…/bkash-callback).
  bool _isReturn(String url) {
    final path = Uri.tryParse(url)?.path ?? '';
    return path.endsWith('/api/payments/bkash/callback') || path.endsWith('/bkash-callback');
  }

  Future<void> _back(String url) async {
    if (_returned) return;
    _returned = true;
    setState(() => _step = _Step.confirming);
    final uri = Uri.parse(url);
    final api = ref.read(apiProvider);
    final paymentId = uri.queryParameters['paymentID'] ?? uri.queryParameters['paymentId'] ?? '';
    // At the server's address, the server records the payment; at the result page it already has.
    final status = uri.path.endsWith('/api/payments/bkash/callback')
        ? await api.bkashReturn(uri)
        : (uri.queryParameters['status'] ?? 'failure');
    if (!mounted) return;
    if (status.startsWith('cancel')) {
      setState(() => _step = _Step.cancelled);
      return;
    }
    try {
      // Only the server's word (from bKash) decides that it is paid.
      final result = await api.bkashExecute(paymentId);
      if (!mounted) return;
      if (result.isPaid) {
        context.pushReplacement('/order/placed', extra: widget.order.paid(result.trxId));
        return;
      }
      setState(() {
        _step = _Step.failed;
        _message = result.errorMessage ?? 'The payment was not completed.';
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _step = _Step.failed;
        _message = e.message;
      });
    }
  }

  Future<void> _leave() async {
    if (_step != _Step.paying) {
      _close();
      return;
    }
    final leave = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Leave the payment?'),
        content: const Text('Your order is saved, but it is not paid yet.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Keep paying')),
          TextButton(onPressed: () => Navigator.pop(context, true), child: const Text('Leave')),
        ],
      ),
    );
    if (leave == true && mounted) setState(() => _step = _Step.cancelled);
  }

  void _close() => context.go('/');

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _leave();
      },
      child: Scaffold(
        appBar: AppBar(
          leading: IconButton(icon: const Icon(Icons.close), tooltip: 'Close', onPressed: _leave),
          title: const Text('Pay with bKash', style: TextStyle(fontWeight: FontWeight.w600)),
          bottom: _step == _Step.paying && _loading < 100
              ? PreferredSize(
                  preferredSize: const Size.fromHeight(2),
                  child: LinearProgressIndicator(value: _loading / 100, minHeight: 2, color: AppColors.ink),
                )
              : null,
        ),
        body: switch (_step) {
          _Step.paying => WebViewWidget(key: const Key('bkash-page'), controller: _web!),
          _Step.starting => const _Waiting('Opening bKash…'),
          _Step.confirming => const _Waiting('Confirming your payment…'),
          _Step.failed || _Step.cancelled => _NotPaid(
            order: widget.order,
            cancelled: _step == _Step.cancelled,
            message: _message,
            onPayAgain: _start,
            onDone: _close,
          ),
        },
      ),
    );
  }
}

class _Waiting extends StatelessWidget {
  const _Waiting(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Center(
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        CircularProgressIndicator(color: AppColors.ink),
        const SizedBox(height: 16),
        Text(text, style: TextStyle(color: AppColors.muted)),
      ],
    ),
  );
}

/// The order is saved but unpaid: say so, and offer to pay again.
class _NotPaid extends StatelessWidget {
  const _NotPaid({
    required this.order,
    required this.cancelled,
    required this.message,
    required this.onPayAgain,
    required this.onDone,
  });
  final PlacedOrder order;
  final bool cancelled;
  final String? message;
  final VoidCallback onPayAgain;
  final VoidCallback onDone;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          key: const Key('bkash-not-paid'),
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 76,
              height: 76,
              decoration: const BoxDecoration(color: Color(0xFFFEF2F2), shape: BoxShape.circle),
              child: const Icon(Icons.close, color: Color(0xFFB91C1C), size: 40),
            ),
            const SizedBox(height: 18),
            Text(
              cancelled ? 'Payment cancelled' : 'Payment not completed',
              style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700),
            ),
            if (message != null && !cancelled) ...[
              const SizedBox(height: 8),
              Text(
                message!,
                textAlign: TextAlign.center,
                style: TextStyle(color: AppColors.muted),
              ),
            ],
            const SizedBox(height: 16),
            Text(
              'Your order ${order.orderNumber} is saved but not paid yet'
              '${order.total == null ? '' : ' (${taka(order.total!)})'}.',
              textAlign: TextAlign.center,
              style: const TextStyle(height: 1.4),
            ),
            const SizedBox(height: 24),
            SizedBox(
              width: double.infinity,
              height: 52,
              child: FilledButton(
                key: const Key('bkash-pay-again'),
                onPressed: onPayAgain,
                child: const Text('Pay again with bKash'),
              ),
            ),
            const SizedBox(height: 8),
            TextButton(
              onPressed: onDone,
              style: TextButton.styleFrom(foregroundColor: AppColors.ink),
              child: const Text('Back to the shop'),
            ),
          ],
        ),
      ),
    );
  }
}
