import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../data/cart.dart';
import '../../data/recently_viewed.dart';
import '../../data/models.dart';
import '../../widgets/common.dart';
import '../../widgets/shop_bar.dart';
import '../../widgets/product_card.dart';
import 'product_choice.dart';
import 'size_chart_sheet.dart';

/// One product: photos, choices (colour, fit, size), price, stock, details,
/// reviews and similar products. Works like the website's product page.
class ProductScreen extends ConsumerWidget {
  const ProductScreen({super.key, required this.productId});
  final int productId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final details = ref.watch(productDetailsProvider(productId));
    return details.when(
      loading: () => Scaffold(
        appBar: AppBar(),
        body: Center(child: CircularProgressIndicator(color: AppColors.ink)),
      ),
      error: (e, _) => Scaffold(
        appBar: AppBar(),
        body: ErrorRetry(message: e.toString(), onRetry: () => ref.invalidate(productDetailsProvider(productId))),
      ),
      data: (d) => _ProductView(details: d),
    );
  }
}

class _ProductView extends ConsumerStatefulWidget {
  const _ProductView({required this.details});
  final ProductDetails details;

  @override
  ConsumerState<_ProductView> createState() => _ProductViewState();
}

class _ProductViewState extends ConsumerState<_ProductView> {
  late ProductChoice choice = ProductChoice.start(widget.details.product, widget.details.variants);
  final _gallery = PageController();

  @override
  void initState() {
    super.initState();
    // Remembered for "Recently viewed" once the page is up (not while it is being built).
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) ref.read(recentlyViewedProvider.notifier).viewed(widget.details.product);
    });
  }

  int _photo = 0;

  Product get product => widget.details.product;

  @override
  void dispose() {
    _gallery.dispose();
    super.dispose();
  }

  void _update(ProductChoice next) {
    final photosBefore = choice.photos(widget.details.images);
    setState(() => choice = next);
    // A new colour shows its own photos from the first one.
    if (next.photos(widget.details.images).map((i) => i.id).join() != photosBefore.map((i) => i.id).join()) {
      _photo = 0;
      if (_gallery.hasClients) _gallery.jumpToPage(0);
    }
  }

  void _addToCart(double price) {
    final p = product;
    final v = choice.variant;
    final added = ref
        .read(cartProvider.notifier)
        .add(
          CartLine(
            productId: p.id,
            variantId: v?.id,
            quantity: 1,
            name: p.name,
            label: choice.summary,
            imageUrl: choice.photos(widget.details.images).firstOrNull?.url ?? p.imageUrl,
            unitPrice: price,
            maxQuantity: v != null ? v.stockQuantity : p.stockQuantity,
          ),
        );
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          // A message with a button stays until closed unless told otherwise;
          // this one goes by itself so it never covers the Checkout button.
          persist: false,
          duration: const Duration(seconds: 4),
          content: Text(added ? 'Added to your cart' : 'You already have as many as we can send of this.'),
          action: SnackBarAction(label: 'View cart', onPressed: () => context.go('/cart')),
        ),
      );
  }

  @override
  Widget build(BuildContext context) {
    final p = product;
    final book = ref.watch(priceBookProvider);
    final settings = ref.watch(storeSettingsProvider).value;
    final charts = ref.watch(sizeChartsProvider).value ?? const {};
    final price = book.priceOf(p, choice.basePrice);
    final regular = choice.regularPrice;
    final off = regular > price + 0.001 ? ((regular - price) / regular * 100).round() : 0;
    final photos = choice.photos(widget.details.images);
    final chart = choice.sizeChart(charts, settings);
    final status = choice.stockStatus;
    final paymentNote = _paymentNote(p, settings);

    return Scaffold(
      body: CustomScrollView(
        slivers: [
          SliverAppBar(
            pinned: true,
            actions: [
              Padding(
                padding: const EdgeInsets.only(right: 12),
                child: HeartButton(product: p, size: 38),
              ),
            ],
            // A white circle keeps Back easy to see on any photo.
            leading: Padding(
              padding: const EdgeInsets.all(8),
              child: Material(
                color: AppColors.card,
                shape: const CircleBorder(),
                elevation: 1,
                child: IconButton(
                  icon: const Icon(Icons.arrow_back, size: 20),
                  tooltip: 'Back',
                  padding: EdgeInsets.zero,
                  onPressed: () => Navigator.of(context).maybePop(),
                ),
              ),
            ),
            expandedHeight: MediaQuery.sizeOf(context).width * 1.15,
            backgroundColor: AppColors.page,
            flexibleSpace: FlexibleSpaceBar(
              background: _Gallery(
                controller: _gallery,
                photos: photos.isEmpty && p.imageUrl != null ? [p.imageUrl!] : photos.map((i) => i.url).toList(),
                index: _photo,
                fallback: p.name,
                onPage: (i) => setState(() => _photo = i),
              ),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            sliver: SliverList.list(
              children: [
                if (p.categoryName != null)
                  Text(
                    p.categoryName!.toUpperCase(),
                    style: TextStyle(fontSize: 12, letterSpacing: 1.2, color: AppColors.muted),
                  ),
                const SizedBox(height: 4),
                Text(
                  p.name,
                  key: const Key('product-name'),
                  style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700, height: 1.2),
                ),
                if (p.productCode != null) ...[
                  const SizedBox(height: 2),
                  Text(p.productCode!, style: TextStyle(color: AppColors.muted, fontSize: 13)),
                ],
                if (p.totalReviews > 0) ...[
                  const SizedBox(height: 6),
                  _Stars(rating: p.avgRating, label: '${p.avgRating.toStringAsFixed(1)} (${p.totalReviews})'),
                ],
                const SizedBox(height: 12),
                Row(
                  children: [
                    KeyedSubtree(
                      key: const Key('product-price'),
                      child: PriceLine(price: price, was: regular, size: 22),
                    ),
                    if (off > 0) ...[
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                        decoration: BoxDecoration(color: AppColors.ink, borderRadius: BorderRadius.circular(999)),
                        child: Text(
                          '$off% off',
                          style: TextStyle(color: AppColors.onInk, fontSize: 12, fontWeight: FontWeight.w700),
                        ),
                      ),
                    ],
                  ],
                ),
                const SizedBox(height: 6),
                Text(
                  choice.stockText(status),
                  key: const Key('stock-text'),
                  style: TextStyle(
                    fontWeight: FontWeight.w600,
                    color: switch (status) {
                      'OUT_OF_STOCK' => const Color(0xFFB91C1C),
                      'LOW_STOCK' => const Color(0xFFB45309),
                      _ => const Color(0xFF15803D),
                    },
                  ),
                ),
                if (paymentNote != null) ...[
                  const SizedBox(height: 6),
                  Row(
                    children: [
                      Icon(Icons.info_outline, size: 16, color: AppColors.muted),
                      const SizedBox(width: 6),
                      Text(
                        paymentNote,
                        key: const Key('payment-note'),
                        style: TextStyle(color: AppColors.muted),
                      ),
                    ],
                  ),
                ],
                for (final option in p.options) ...[
                  const SizedBox(height: 20),
                  _Label('${option.name}: ${choice.valueName(option)}'),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 10,
                    runSpacing: 10,
                    children: [
                      for (final v in option.values)
                        _OptionButton(
                          option: option,
                          value: v,
                          selected: choice.choices[option.id] == v.id,
                          available: choice.hasStockWith(option.id, v.id),
                          onTap: () => _update(choice.withChoice(option.id, v.id)),
                        ),
                    ],
                  ),
                ],
                if (choice.fits.length > 1) ...[
                  const SizedBox(height: 20),
                  const _Label('Fit'),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      for (final f in choice.fits)
                        _PickButton(
                          key: Key('fit-$f'),
                          label: choice.fitLabel(f),
                          selected: choice.fit == f,
                          available: true,
                          onTap: () => _update(choice.withFit(f)),
                        ),
                    ],
                  ),
                ] else if (choice.fits.length == 1) ...[
                  const SizedBox(height: 14),
                  Text('Fit: ${choice.fitLabel(choice.fits.first)}', style: TextStyle(color: AppColors.muted)),
                ],
                if (choice.sizes.isNotEmpty) ...[
                  const SizedBox(height: 20),
                  Row(
                    children: [
                      const Expanded(child: _Label('Size')),
                      if (chart != null)
                        TextButton.icon(
                          key: const Key('size-chart-button'),
                          onPressed: () =>
                              showSizeChart(context, chart, choice.fit == null ? null : choice.fitLabel(choice.fit!)),
                          icon: const Icon(Icons.straighten, size: 18),
                          label: const Text('Size chart'),
                          style: TextButton.styleFrom(foregroundColor: AppColors.ink),
                        ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      for (final s in choice.sizes)
                        _PickButton(
                          key: Key('size-$s'),
                          label: s,
                          selected: choice.size == s,
                          available: choice.isSizeAvailable(s),
                          onTap: () => _update(choice.withSize(s)),
                        ),
                    ],
                  ),
                ],
                const SizedBox(height: 24),
                if (p.description.trim().isNotEmpty) ...[
                  const _Label('Details'),
                  const SizedBox(height: 8),
                  Text(p.description.trim(), style: const TextStyle(height: 1.5)),
                ],
                if ((p.material ?? '').isNotEmpty || (p.careInstructions ?? '').isNotEmpty) ...[
                  const SizedBox(height: 16),
                  if ((p.material ?? '').isNotEmpty) Text('Material: ${p.material}'),
                  if ((p.careInstructions ?? '').isNotEmpty) Text('Care: ${p.careInstructions}'),
                ],
                if ((settings?.returnPolicyText ?? '').isNotEmpty) ...[
                  const SizedBox(height: 16),
                  Card(
                    child: ExpansionTile(
                      shape: const Border(),
                      leading: const Icon(Icons.assignment_return_outlined),
                      title: const Text('Returns & exchange', style: TextStyle(fontWeight: FontWeight.w600)),
                      childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                      children: [Text(settings!.returnPolicyText!, style: const TextStyle(height: 1.5))],
                    ),
                  ),
                ],
                const SizedBox(height: 24),
                _Reviews(product: p),
              ],
            ),
          ),
          SliverToBoxAdapter(child: _Related(productId: p.id)),
          const SliverToBoxAdapter(child: SizedBox(height: 24)),
        ],
      ),
      bottomNavigationBar: SafeArea(
        child: Container(
          padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
          decoration: BoxDecoration(
            color: AppColors.card,
            border: Border(top: BorderSide(color: AppColors.line)),
          ),
          child: SizedBox(
            height: 52,
            child: FilledButton(
              key: const Key('add-to-cart'),
              onPressed: choice.canAddToCart ? () => _addToCart(price) : null,
              child: Text(choice.addToCartLabel, style: const TextStyle(fontSize: 16)),
            ),
          ),
        ),
      ),
    );
  }

  /// "bKash payment only" when this product can be paid fewer ways than the shop takes.
  String? _paymentNote(Product p, StoreSettings? s) {
    if (p.blockedPaymentMethods.isEmpty) return null;
    final shop = [if (s?.payCod ?? true) 'CASH', if (s?.payBkash ?? true) 'BKASH', if (s?.payCard ?? true) 'CARD'];
    final left = shop.where((m) => !p.blockedPaymentMethods.contains(m)).toList();
    if (left.length == shop.length || left.isEmpty) return null;
    const names = {'CASH': 'Cash on delivery', 'BKASH': 'bKash payment', 'CARD': 'Card payment'};
    return '${left.map((m) => names[m]).join(' or ')} only';
  }
}

class _Label extends StatelessWidget {
  const _Label(this.text);
  final String text;
  @override
  Widget build(BuildContext context) => Text(text, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600));
}

/// Swipeable photos with dots underneath.
class _Gallery extends StatelessWidget {
  const _Gallery({
    required this.controller,
    required this.photos,
    required this.index,
    required this.fallback,
    required this.onPage,
  });
  final PageController controller;
  final List<String> photos;
  final int index;
  final String fallback;
  final ValueChanged<int> onPage;

  @override
  Widget build(BuildContext context) {
    if (photos.isEmpty) return NetImage(url: null, fallback: fallback);
    return Stack(
      children: [
        PageView.builder(
          key: const Key('gallery'),
          controller: controller,
          itemCount: photos.length,
          onPageChanged: onPage,
          itemBuilder: (_, i) => NetImage(url: photos[i], fallback: fallback),
        ),
        if (photos.length > 1)
          Positioned(
            bottom: 14,
            left: 0,
            right: 0,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                for (var i = 0; i < photos.length; i++)
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    margin: const EdgeInsets.symmetric(horizontal: 3),
                    width: i == index ? 18 : 7,
                    height: 7,
                    decoration: BoxDecoration(
                      color: i == index ? AppColors.ink : Colors.white,
                      borderRadius: BorderRadius.circular(99),
                      border: Border.all(color: AppColors.ink.withValues(alpha: 0.3)),
                    ),
                  ),
              ],
            ),
          ),
      ],
    );
  }
}

class _OptionButton extends StatelessWidget {
  const _OptionButton({
    required this.option,
    required this.value,
    required this.selected,
    required this.available,
    required this.onTap,
  });
  final ProductOption option;
  final OptionValue value;
  final bool selected;
  final bool available;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colour = option.isColour ? _hex(value.hex) : null;
    if (colour != null) {
      return Tooltip(
        message: value.value,
        child: InkWell(
          key: Key('option-${value.id}'),
          customBorder: const CircleBorder(),
          onTap: onTap,
          child: Container(
            width: 40,
            height: 40,
            padding: const EdgeInsets.all(3),
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: Border.all(color: selected ? AppColors.ink : AppColors.line, width: selected ? 2 : 1),
            ),
            child: Opacity(
              opacity: available ? 1 : 0.35,
              child: Container(
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: colour,
                  border: Border.all(color: AppColors.line),
                ),
              ),
            ),
          ),
        ),
      );
    }
    return _PickButton(
      key: Key('option-${value.id}'),
      label: value.value,
      selected: selected,
      available: available,
      onTap: onTap,
    );
  }

  static Color? _hex(String? hex) {
    final h = (hex ?? '').replaceFirst('#', '');
    if (h.length != 6) return null;
    final v = int.tryParse(h, radix: 16);
    return v == null ? null : Color(0xFF000000 | v);
  }
}

/// A size, fit or option box: black when picked, struck through when sold out.
class _PickButton extends StatelessWidget {
  const _PickButton({
    super.key,
    required this.label,
    required this.selected,
    required this.available,
    required this.onTap,
  });
  final String label;
  final bool selected;
  final bool available;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: available ? onTap : null,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        constraints: const BoxConstraints(minWidth: 56),
        height: 46,
        padding: const EdgeInsets.symmetric(horizontal: 14),
        decoration: BoxDecoration(
          color: selected ? AppColors.ink : AppColors.card,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: selected ? AppColors.ink : AppColors.line),
        ),
        // Only as wide as the label needs, so the boxes sit side by side.
        child: Center(
          widthFactor: 1,
          child: Text(
            label,
            style: TextStyle(
              fontWeight: FontWeight.w600,
              color: selected ? AppColors.onInk : (available ? AppColors.ink : AppColors.muted),
              decoration: available ? null : TextDecoration.lineThrough,
            ),
          ),
        ),
      ),
    );
  }
}

class _Stars extends StatelessWidget {
  const _Stars({required this.rating, this.label});
  final double rating;
  final String? label;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        for (var i = 1; i <= 5; i++)
          Icon(
            rating >= i ? Icons.star : (rating >= i - 0.5 ? Icons.star_half : Icons.star_border),
            size: 16,
            color: const Color(0xFFF59E0B),
          ),
        if (label != null) ...[
          const SizedBox(width: 6),
          Text(label!, style: TextStyle(color: AppColors.muted, fontSize: 13)),
        ],
      ],
    );
  }
}

/// The first few reviews.
class _Reviews extends ConsumerWidget {
  const _Reviews({required this.product});
  final Product product;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final reviews = ref.watch(reviewsProvider(product.id)).value;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Expanded(child: _Label('Reviews')),
            if (product.totalReviews > 0) _Stars(rating: product.avgRating, label: '${product.totalReviews}'),
          ],
        ),
        const SizedBox(height: 8),
        if (reviews == null || reviews.items.isEmpty)
          Text('No reviews yet.', style: TextStyle(color: AppColors.muted))
        else
          for (final r in reviews.items)
            Padding(
              padding: const EdgeInsets.only(bottom: 14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Text(r.userName, style: const TextStyle(fontWeight: FontWeight.w600)),
                      const SizedBox(width: 8),
                      _Stars(rating: r.rating.toDouble()),
                    ],
                  ),
                  if (r.comment.trim().isNotEmpty) ...[
                    const SizedBox(height: 4),
                    Text(r.comment.trim(), style: const TextStyle(height: 1.4)),
                  ],
                ],
              ),
            ),
      ],
    );
  }
}

class _Related extends ConsumerWidget {
  const _Related({required this.productId});
  final int productId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final related = ref.watch(relatedProvider(productId)).value ?? const [];
    if (related.isEmpty) return const SizedBox.shrink();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Padding(padding: EdgeInsets.fromLTRB(16, 16, 16, 12), child: _Label('You may also like')),
        ProductRow(products: related),
      ],
    );
  }
}
