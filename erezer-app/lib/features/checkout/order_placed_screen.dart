import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../app/theme.dart';
import '../../data/models.dart';
import '../../data/pricing.dart';
import '../../data/push.dart';

/// Thank-you page with the order number to keep.
class OrderPlacedScreen extends StatelessWidget {
  const OrderPlacedScreen({super.key, required this.order});
  final PlacedOrder? order;

  @override
  Widget build(BuildContext context) {
    final o = order;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 84,
                  height: 84,
                  decoration: const BoxDecoration(color: AppColors.ink, shape: BoxShape.circle),
                  child: const Icon(Icons.check, color: Colors.white, size: 44),
                ),
                const SizedBox(height: 20),
                const Text(
                  'Thank you! Your order is placed.',
                  textAlign: TextAlign.center,
                  style: TextStyle(fontSize: 22, fontWeight: FontWeight.w700),
                ),
                if (o != null) ...[
                  const SizedBox(height: 16),
                  const Text('Your order number', style: TextStyle(color: AppColors.muted)),
                  const SizedBox(height: 4),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        o.orderNumber,
                        key: const Key('order-number'),
                        style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w800, letterSpacing: 1),
                      ),
                      IconButton(
                        icon: const Icon(Icons.copy, size: 20),
                        tooltip: 'Copy',
                        onPressed: () {
                          Clipboard.setData(ClipboardData(text: o.orderNumber));
                          ScaffoldMessenger.of(context)
                              .showSnackBar(const SnackBar(content: Text('Order number copied')));
                        },
                      ),
                    ],
                  ),
                  if (o.bkashTrxId != null) ...[
                    Text(
                      o.total == null ? 'Paid with bKash.' : '${taka(o.total!)} paid with bKash.',
                      key: const Key('paid-bkash'),
                      style: const TextStyle(fontWeight: FontWeight.w600, color: Color(0xFF15803D)),
                    ),
                    if (o.bkashTrxId!.isNotEmpty)
                      Text('Transaction ${o.bkashTrxId}', style: const TextStyle(color: AppColors.muted)),
                  ] else if (o.total != null)
                    Text('Total ${taka(o.total!)}, paid on delivery.'),
                  const SizedBox(height: 12),
                  const Text(
                    'Keep this number to track your order.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: AppColors.muted, height: 1.4),
                  ),
                  const SizedBox(height: 20),
                  const _OrderUpdatesCard(),
                ],
                const SizedBox(height: 28),
                FilledButton(
                  key: const Key('continue-shopping'),
                  onPressed: () => context.go('/'),
                  child: const Text('Continue shopping'),
                ),
                if (o != null)
                  TextButton(
                    key: const Key('track-this-order'),
                    onPressed: () => context.go('/account/track?number=${Uri.encodeQueryComponent(o.orderNumber)}'),
                    style: TextButton.styleFrom(foregroundColor: AppColors.ink),
                    child: const Text('Track this order'),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// "Get updates about this order": asks to allow notifications, right when they
/// are wanted. Hidden when notifications are not set up in this build.
class _OrderUpdatesCard extends ConsumerWidget {
  const _OrderUpdatesCard();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final push = ref.watch(pushProvider);
    if (!push.available) return const SizedBox.shrink();
    if (push.allowed) {
      return const Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.notifications_active_outlined, size: 18, color: Color(0xFF15803D)),
          SizedBox(width: 6),
          Text('We will let you know when it ships.', style: TextStyle(color: Color(0xFF15803D))),
        ],
      );
    }
    return Container(
      key: const Key('order-updates'),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.line),
      ),
      child: Column(
        children: [
          const Text('Get updates about this order', style: TextStyle(fontWeight: FontWeight.w700)),
          const SizedBox(height: 4),
          const Text(
            'A notification when it is confirmed, shipped and delivered.',
            textAlign: TextAlign.center,
            style: TextStyle(color: AppColors.muted),
          ),
          const SizedBox(height: 10),
          OutlinedButton.icon(
            key: const Key('turn-on-updates'),
            onPressed: () => ref.read(pushProvider.notifier).askPermission(),
            icon: const Icon(Icons.notifications_outlined),
            label: const Text('Turn on order updates'),
            style: OutlinedButton.styleFrom(foregroundColor: AppColors.ink, shape: const StadiumBorder()),
          ),
        ],
      ),
    );
  }
}
