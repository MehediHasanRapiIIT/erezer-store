import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../app/nav.dart';
import '../app/theme.dart';
import '../data/api_client.dart';
import '../data/models.dart';
import '../data/pricing.dart';
import 'common.dart';

/// The price to pay, with the regular price struck through when it's lower.
class PriceLine extends StatelessWidget {
  const PriceLine({super.key, required this.price, this.was, this.size = 15});
  final double price;
  final double? was;
  final double size;

  @override
  Widget build(BuildContext context) {
    final onSale = was != null && was! > price + 0.001;
    return Wrap(
      crossAxisAlignment: WrapCrossAlignment.center,
      spacing: 6,
      children: [
        Text(
          taka(price),
          style: TextStyle(fontSize: size, fontWeight: FontWeight.w700),
        ),
        if (onSale)
          Text(
            taka(was!),
            style: TextStyle(fontSize: size - 3, color: AppColors.muted, decoration: TextDecoration.lineThrough),
          ),
      ],
    );
  }
}

/// A product in a list: photo, name and price. Tapping opens the product page.
class ProductCard extends ConsumerWidget {
  const ProductCard({super.key, required this.product});
  final Product product;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final p = product;
    final price = ref.watch(priceBookProvider).cardPrice(p);
    final off = p.price > price + 0.001 ? ((p.price - price) / p.price * 100).round() : 0;
    return InkWell(
      key: Key('product-${p.id}'),
      borderRadius: BorderRadius.circular(14),
      onTap: () => openProduct(context, p.id),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AspectRatio(
            aspectRatio: 4 / 5,
            child: ClipRRect(
              borderRadius: BorderRadius.circular(14),
              child: Stack(
                fit: StackFit.expand,
                children: [
                  NetImage(url: p.imageUrl, fallback: p.name),
                  if (off > 0 && !p.isOutOfStock)
                    Positioned(left: 8, top: 8, child: _Badge(text: '-$off%', dark: true)),
                  if (p.isNewArrival && off == 0 && !p.isOutOfStock)
                    const Positioned(left: 8, top: 8, child: _Badge(text: 'New', dark: false)),
                  if (p.isOutOfStock)
                    Container(
                      color: AppColors.card.withValues(alpha: 0.55),
                      alignment: Alignment.center,
                      child: const _Badge(text: 'Sold out', dark: true),
                    ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            p.name,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w500, height: 1.25),
          ),
          const SizedBox(height: 4),
          PriceLine(price: price, was: p.price, size: 14.5),
        ],
      ),
    );
  }
}

class _Badge extends StatelessWidget {
  const _Badge({required this.text, required this.dark});
  final String text;
  final bool dark;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(color: dark ? AppColors.ink : AppColors.card, borderRadius: BorderRadius.circular(999)),
      child: Text(
        text,
        style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: dark ? AppColors.onInk : AppColors.ink),
      ),
    );
  }
}

/// A sideways row of product cards, for the home page and "You may also like".
class ProductRow extends StatelessWidget {
  const ProductRow({super.key, required this.products});
  final List<Product> products;

  @override
  Widget build(BuildContext context) {
    // Photo (150 wide, 4:5) plus name and price lines, grown with the phone's text size.
    final words = MediaQuery.textScalerOf(context).scale(64);
    return SizedBox(
      height: 187.5 + 12 + words,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        itemCount: products.length,
        separatorBuilder: (_, _) => const SizedBox(width: 12),
        itemBuilder: (_, i) => SizedBox(width: 150, child: ProductCard(product: products[i])),
      ),
    );
  }
}

/// Grid spacing used by every product list.
const productGridDelegate = SliverGridDelegateWithFixedCrossAxisCount(
  crossAxisCount: 2,
  mainAxisSpacing: 18,
  crossAxisSpacing: 12,
  childAspectRatio: 0.6,
);
