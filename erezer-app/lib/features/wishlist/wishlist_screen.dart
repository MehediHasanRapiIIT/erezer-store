import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../app/nav.dart';
import '../../data/api_client.dart';
import '../../data/wishlist.dart';
import '../../widgets/common.dart';
import '../home/home_bands.dart';

/// The products the customer hearted.
class WishlistScreen extends ConsumerWidget {
  const WishlistScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final saved = ref.watch(wishlistProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Wishlist', style: TextStyle(fontWeight: FontWeight.w600)),
      ),
      body: saved.isEmpty
          ? Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const EmptyState(
                  icon: Icons.favorite_border,
                  title: 'Your wishlist is empty',
                  text: 'Tap the ♡ on a product to keep it here.',
                ),
                FilledButton(onPressed: () => openAllProducts(context), child: const Text('Start shopping')),
              ],
            )
          : ListView(
              key: const Key('wishlist-list'),
              padding: const EdgeInsets.only(top: 16, bottom: 24),
              children: [ProductGridBand(products: saved, max: saved.length)],
            ),
    );
  }
}

/// Every product in the running flash sale.
class FlashSaleScreen extends ConsumerWidget {
  const FlashSaleScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final sale = ref.watch(flashSaleProvider).value;
    return Scaffold(
      appBar: AppBar(
        title: Text(sale?.name ?? 'Flash sale', style: const TextStyle(fontWeight: FontWeight.w600)),
      ),
      body: sale == null
          ? const EmptyState(icon: Icons.bolt_outlined, title: 'No sale right now', text: 'Please check back soon.')
          : ListView(
              padding: const EdgeInsets.only(bottom: 24),
              children: [FlashSaleBand(sale: sale)],
            ),
    );
  }
}
