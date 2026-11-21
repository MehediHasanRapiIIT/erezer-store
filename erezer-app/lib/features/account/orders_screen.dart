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

/// "Not paid" / "Paid" for a bKash order, as the website words it.
String? paymentWords(String method, String? state, {double? refunded, double? total}) {
  if (method.toUpperCase() != 'BKASH') return 'Cash on delivery';
  if (state == 'PAID') {
    if (refunded != null && refunded > 0) {
      return total != null && refunded >= total ? 'bKash · Refunded' : 'bKash · Part refunded';
    }
    return 'bKash · Paid';
  }
  return 'bKash · Not paid yet';
}

/// The logged-in customer's orders, newest first, loading more as they scroll.
class OrdersScreen extends ConsumerStatefulWidget {
  const OrdersScreen({super.key});

  @override
  ConsumerState<OrdersScreen> createState() => _OrdersScreenState();
}

class _OrdersScreenState extends ConsumerState<OrdersScreen> {
  final _orders = <Order>[];
  int _page = 0;
  bool _done = false;
  bool _loading = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _more();
  }

  Future<void> _more() async {
    if (_loading || _done) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final page = await ref
          .read(authProvider.notifier)
          .call((api, s) => api.orders(s.userId, s.accessToken, page: _page));
      if (!mounted) return;
      setState(() {
        _orders.addAll(page.items);
        _page++;
        _done = page.isLast || page.items.isEmpty;
      });
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _reload() async {
    setState(() {
      _orders.clear();
      _page = 0;
      _done = false;
    });
    await _more();
  }

  @override
  Widget build(BuildContext context) {
    final Widget body;
    if (_orders.isEmpty && _error != null) {
      body = ErrorRetry(message: _error!, onRetry: _more);
    } else if (_orders.isEmpty && !_done) {
      body = const Center(child: CircularProgressIndicator(color: AppColors.ink));
    } else if (_orders.isEmpty) {
      body = Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const EmptyState(
            icon: Icons.receipt_long_outlined,
            title: 'No orders yet',
            text: 'Your orders will show up here.',
          ),
          FilledButton(onPressed: () => context.go('/categories'), child: const Text('Start shopping')),
        ],
      );
    } else {
      body = RefreshIndicator(
        color: AppColors.ink,
        onRefresh: _reload,
        child: NotificationListener<ScrollNotification>(
          onNotification: (n) {
            if (n.metrics.extentAfter < 400) _more();
            return false;
          },
          child: ListView.separated(
            key: const Key('orders-list'),
            padding: const EdgeInsets.all(16),
            itemCount: _orders.length + 1,
            separatorBuilder: (_, _) => const SizedBox(height: 12),
            itemBuilder: (context, i) {
              if (i == _orders.length) {
                return Center(
                  child: _loading
                      ? const CircularProgressIndicator(color: AppColors.ink)
                      : (_error != null ? TextButton(onPressed: _more, child: const Text('Tap to load more')) : null),
                );
              }
              final o = _orders[i];
              return Card(
                key: Key('order-${o.orderNumber}'),
                clipBehavior: Clip.antiAlias,
                child: InkWell(
                  onTap: () async {
                    await context.push('/account/orders/${o.id}');
                    // Paid or cancelled meanwhile: show it.
                    if (mounted) _reload();
                  },
                  child: Padding(
                    padding: const EdgeInsets.all(14),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                o.orderNumber,
                                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
                              ),
                            ),
                            StatusChip(o.status),
                          ],
                        ),
                        const SizedBox(height: 4),
                        if (o.createdAt != null)
                          Text(shortDate(o.createdAt!), style: const TextStyle(color: AppColors.muted, fontSize: 13)),
                        const SizedBox(height: 8),
                        Text(
                          o.lines.map((l) => '${l.quantity} × ${l.name}').join(', '),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                        const SizedBox(height: 8),
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                paymentWords(o.paymentMethod, o.paymentState, refunded: o.refunded, total: o.total) ??
                                    '',
                                style: TextStyle(
                                  color: o.canPayNow ? const Color(0xFFB45309) : AppColors.muted,
                                  fontWeight: o.canPayNow ? FontWeight.w600 : FontWeight.w400,
                                ),
                              ),
                            ),
                            Text(taka(o.total), style: const TextStyle(fontWeight: FontWeight.w700)),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              );
            },
          ),
        ),
      );
    }
    return Scaffold(
      appBar: AppBar(
        title: const Text('My orders', style: TextStyle(fontWeight: FontWeight.w600)),
      ),
      body: body,
    );
  }
}
