import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../app/nav.dart';
import '../../app/theme.dart';
import '../../data/cart.dart';
import '../../data/pricing.dart';
import '../../widgets/common.dart';

/// The bag: each item with its size, quantity buttons and a remove button,
/// and the way to checkout.
class CartScreen extends ConsumerWidget {
  const CartScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final lines = ref.watch(cartProvider);
    final cart = ref.read(cartProvider.notifier);
    final subtotal = lines.fold<double>(0, (sum, l) => sum + l.unitPrice * l.quantity);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Cart', style: TextStyle(fontWeight: FontWeight.w600)),
      ),
      body: lines.isEmpty
          ? Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const EmptyState(
                  icon: Icons.shopping_bag_outlined,
                  title: 'Your cart is empty',
                  text: 'Products you add will show up here.',
                ),
                FilledButton(onPressed: () => context.go('/categories'), child: const Text('Start shopping')),
              ],
            )
          : ListView.separated(
              key: const Key('cart-lines'),
              padding: const EdgeInsets.all(16),
              itemCount: lines.length,
              separatorBuilder: (_, _) => const SizedBox(height: 12),
              itemBuilder: (context, i) {
                final l = lines[i];
                return Card(
                  key: Key('cart-line-${l.key}'),
                  clipBehavior: Clip.antiAlias,
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      InkWell(
                        onTap: () => openProduct(context, l.productId),
                        child: SizedBox(
                          width: 92,
                          height: 115,
                          child: NetImage(url: l.imageUrl, fallback: l.name),
                        ),
                      ),
                      Expanded(
                        child: Padding(
                          padding: const EdgeInsets.fromLTRB(12, 10, 4, 8),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                l.name,
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(fontWeight: FontWeight.w600),
                              ),
                              if (l.label.isNotEmpty)
                                Text(l.label, style: const TextStyle(color: AppColors.muted, fontSize: 13)),
                              const SizedBox(height: 4),
                              Text(taka(l.unitPrice * l.quantity), style: const TextStyle(fontWeight: FontWeight.w700)),
                              const SizedBox(height: 6),
                              Row(
                                children: [
                                  _QtyButton(
                                    icon: l.quantity == 1 ? Icons.delete_outline : Icons.remove,
                                    tooltip: l.quantity == 1 ? 'Remove' : 'One less',
                                    onTap: () => cart.setQuantity(l.key, l.quantity - 1),
                                  ),
                                  SizedBox(
                                    width: 36,
                                    child: Text(
                                      '${l.quantity}',
                                      key: Key('qty-${l.key}'),
                                      textAlign: TextAlign.center,
                                      style: const TextStyle(fontWeight: FontWeight.w600),
                                    ),
                                  ),
                                  _QtyButton(
                                    icon: Icons.add,
                                    tooltip: 'One more',
                                    onTap: cart.canAddOne(l) ? () => cart.setQuantity(l.key, l.quantity + 1) : null,
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.close, size: 20),
                        tooltip: 'Remove',
                        onPressed: () => cart.remove(l.key),
                      ),
                    ],
                  ),
                );
              },
            ),
      bottomNavigationBar: lines.isEmpty
          ? null
          : SafeArea(
              child: Container(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
                decoration: const BoxDecoration(
                  color: Colors.white,
                  border: Border(top: BorderSide(color: AppColors.line)),
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Row(
                      children: [
                        const Expanded(child: Text('Subtotal', style: TextStyle(fontSize: 16))),
                        Text(
                          taka(subtotal),
                          key: const Key('cart-subtotal'),
                          style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                        ),
                      ],
                    ),
                    const SizedBox(height: 2),
                    const Align(
                      alignment: Alignment.centerLeft,
                      child: Text(
                        'Delivery is added at checkout.',
                        style: TextStyle(color: AppColors.muted, fontSize: 13),
                      ),
                    ),
                    const SizedBox(height: 10),
                    SizedBox(
                      width: double.infinity,
                      height: 52,
                      child: FilledButton(
                        key: const Key('go-checkout'),
                        onPressed: () => context.push('/cart/checkout'),
                        child: const Text('Checkout', style: TextStyle(fontSize: 16)),
                      ),
                    ),
                  ],
                ),
              ),
            ),
    );
  }
}

class _QtyButton extends StatelessWidget {
  const _QtyButton({required this.icon, required this.tooltip, required this.onTap});
  final IconData icon;
  final String tooltip;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip,
      child: InkWell(
        onTap: onTap,
        customBorder: const CircleBorder(),
        child: Container(
          width: 34,
          height: 34,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            border: Border.all(color: AppColors.line),
          ),
          child: Icon(icon, size: 18, color: onTap == null ? AppColors.line : AppColors.ink),
        ),
      ),
    );
  }
}
