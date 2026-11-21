import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../data/auth.dart';
import '../../data/models.dart';
import '../../data/pricing.dart';
import '../../widgets/common.dart';
import '../../widgets/order_timeline.dart';
import 'orders_screen.dart';

/// One of the customer's orders: progress, items, money, delivery address,
/// "Pay with bKash" while unpaid, and Cancel while the shop allows it.
class OrderDetailScreen extends ConsumerStatefulWidget {
  const OrderDetailScreen({super.key, required this.orderId});
  final String orderId;

  @override
  ConsumerState<OrderDetailScreen> createState() => _OrderDetailScreenState();
}

class _OrderDetailScreenState extends ConsumerState<OrderDetailScreen> {
  Order? _order;
  OrderProgress? _progress;
  String? _error;
  bool _cancelling = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _error = null);
    final auth = ref.read(authProvider.notifier);
    try {
      final order = await auth.call((api, s) => api.order(s.userId, s.accessToken, widget.orderId));
      // The steps' dates are a nice extra: the order shows without them.
      OrderProgress? progress;
      try {
        progress = await auth.call((api, s) => api.orderProgress(s.userId, s.accessToken, widget.orderId));
      } on ApiException {
        progress = null;
      }
      if (!mounted) return;
      setState(() {
        _order = order;
        _progress = progress;
      });
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    }
  }

  Future<void> _cancel() async {
    final reason = TextEditingController();
    final sure = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Cancel this order?'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('This cannot be undone.'),
            const SizedBox(height: 12),
            TextField(
              controller: reason,
              decoration: const InputDecoration(labelText: 'Why? (optional)'),
              maxLength: 200,
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Keep order')),
          TextButton(
            key: const Key('confirm-cancel'),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Cancel order', style: TextStyle(color: Color(0xFFB91C1C))),
          ),
        ],
      ),
    );
    if (sure != true || !mounted) return;
    setState(() => _cancelling = true);
    try {
      await ref
          .read(authProvider.notifier)
          .call((api, s) => api.cancelOrder(s.userId, s.accessToken, widget.orderId, reason.text));
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Your order is cancelled.')));
      await _load();
    } on ApiException catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
    } finally {
      if (mounted) setState(() => _cancelling = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final o = _order;
    return Scaffold(
      appBar: AppBar(
        title: Text(o?.orderNumber ?? 'Order', style: const TextStyle(fontWeight: FontWeight.w600)),
      ),
      body: o == null
          ? (_error != null
                ? ErrorRetry(message: _error!, onRetry: _load)
                : const Center(child: CircularProgressIndicator(color: AppColors.ink)))
          : RefreshIndicator(
              color: AppColors.ink,
              onRefresh: _load,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          o.createdAt == null ? '' : 'Placed ${shortDate(o.createdAt!)}',
                          style: const TextStyle(color: AppColors.muted),
                        ),
                      ),
                      StatusChip(o.status),
                    ],
                  ),
                  const SizedBox(height: 12),
                  if (o.canPayNow) ...[
                    PayNowBox(order: o.asPlaced, failed: o.paymentState == 'FAILED'),
                    const SizedBox(height: 12),
                  ],
                  OrderCard(
                    title: 'Progress',
                    child: OrderTimeline(status: o.status, reached: _progress?.reached ?? const {}),
                  ),
                  if ((o.courierName ?? '').isNotEmpty || (o.trackingNumber ?? '').isNotEmpty)
                    OrderCard(
                      title: 'Courier',
                      child: Text(
                        [o.courierName, o.trackingNumber].whereType<String>().where((s) => s.isNotEmpty).join(' · '),
                      ),
                    ),
                  if ((o.cancellationReason ?? '').isNotEmpty)
                    OrderCard(title: 'Why it was cancelled', child: Text(o.cancellationReason!)),
                  OrderCard(
                    title: 'Items',
                    child: OrderLines(lines: o.lines),
                  ),
                  OrderCard(
                    title: 'Payment',
                    child: Column(
                      children: [
                        SumRow('Delivery', o.deliveryCharge == 0 ? 'Free' : taka(o.deliveryCharge)),
                        SumRow('Total', taka(o.total), bold: true),
                        const SizedBox(height: 6),
                        Align(
                          alignment: Alignment.centerLeft,
                          child: Text(
                            [
                              paymentWords(o.paymentMethod, o.paymentState, refunded: o.refunded, total: o.total),
                              if ((o.bkashTrxId ?? '').isNotEmpty) 'Transaction ${o.bkashTrxId}',
                              if (o.refunded != null && o.refunded! > 0) 'Refunded ${taka(o.refunded!)}',
                            ].whereType<String>().join('\n'),
                            key: const Key('order-payment'),
                            style: const TextStyle(color: AppColors.muted, height: 1.4),
                          ),
                        ),
                      ],
                    ),
                  ),
                  if ((o.deliveryAddress ?? '').isNotEmpty)
                    OrderCard(title: 'Delivery address', child: Text(o.deliveryAddress!)),
                  if (_progress?.canCancel ?? false) ...[
                    const SizedBox(height: 8),
                    OutlinedButton(
                      key: const Key('cancel-order'),
                      onPressed: _cancelling ? null : _cancel,
                      style: OutlinedButton.styleFrom(
                        foregroundColor: const Color(0xFFB91C1C),
                        side: const BorderSide(color: Color(0xFFFECACA)),
                        minimumSize: const Size.fromHeight(50),
                        shape: const StadiumBorder(),
                      ),
                      child: const Text('Cancel order'),
                    ),
                  ],
                ],
              ),
            ),
    );
  }
}

/// "Not paid yet" with a button to pay with bKash now.
class PayNowBox extends StatelessWidget {
  const PayNowBox({super.key, required this.order, required this.failed});
  final PlacedOrder order;
  final bool failed;

  @override
  Widget build(BuildContext context) {
    return Container(
      key: const Key('pay-now'),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFFFFFBEB),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFFFDE68A)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            failed ? 'The last bKash payment did not go through.' : 'This order is not paid yet.',
            style: const TextStyle(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 8),
          FilledButton(
            key: const Key('pay-now-button'),
            onPressed: () => context.push('/order/bkash', extra: order),
            child: Text(order.total == null ? 'Pay with bKash' : 'Pay ${taka(order.total!)} with bKash'),
          ),
        ],
      ),
    );
  }
}

class OrderCard extends StatelessWidget {
  const OrderCard({super.key, required this.title, required this.child});
  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
            const SizedBox(height: 12),
            child,
          ],
        ),
      ),
    ),
  );
}

class OrderLines extends StatelessWidget {
  const OrderLines({super.key, required this.lines});
  final List<OrderLine> lines;

  @override
  Widget build(BuildContext context) => Column(
    children: [
      for (final l in lines)
        Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: SizedBox(
                  width: 52,
                  height: 64,
                  child: NetImage(url: l.imageUrl, fallback: l.name),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(l.name, style: const TextStyle(fontWeight: FontWeight.w600)),
                    Text(
                      [if ((l.size ?? '').isNotEmpty) l.size!, '${l.quantity} × ${taka(l.price)}'].join(' · '),
                      style: const TextStyle(color: AppColors.muted, fontSize: 13),
                    ),
                  ],
                ),
              ),
              Text(taka(l.price * l.quantity), style: const TextStyle(fontWeight: FontWeight.w600)),
            ],
          ),
        ),
    ],
  );
}

class SumRow extends StatelessWidget {
  const SumRow(this.label, this.value, {super.key, this.bold = false});
  final String label;
  final String value;
  final bool bold;

  @override
  Widget build(BuildContext context) {
    final style = TextStyle(fontSize: bold ? 17 : 15, fontWeight: bold ? FontWeight.w700 : FontWeight.w400);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        children: [
          Expanded(child: Text(label, style: style)),
          Text(value, style: style),
        ],
      ),
    );
  }
}
