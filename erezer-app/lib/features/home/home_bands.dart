import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../app/nav.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../data/models.dart';
import '../../data/pricing.dart';
import '../../widgets/common.dart';
import '../../widgets/shop_bar.dart';
import '../product/quick_add.dart';

// The website's home page bands (erezer-store home.page.ts), drawn the same
// way on a phone: edge-to-edge pictures, small spaced-out labels over big
// titles, two-column product grids, black pill buttons.

/// A small spaced-out label above a title ("JUST DROPPED").
class Eyebrow extends StatelessWidget {
  const Eyebrow(this.text, {super.key, this.color});
  final String text;

  /// The theme's quiet grey when not given.
  final Color? color;

  @override
  Widget build(BuildContext context) => Text(
    text.toUpperCase(),
    style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w600, letterSpacing: 3, color: color ?? AppColors.muted),
  );
}

/// "JUST DROPPED / New arrivals ........ View all products"
class BandHeader extends StatelessWidget {
  const BandHeader({super.key, this.eyebrow, required this.title, this.action, this.onAction});
  final String? eyebrow;
  final String title;
  final String? action;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 40, 16, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (eyebrow != null) ...[Eyebrow(eyebrow!), const SizedBox(height: 8)],
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Expanded(
                child: Text(
                  title,
                  style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w600, letterSpacing: -0.6, height: 1.1),
                ),
              ),
              if (action != null)
                InkWell(
                  onTap: onAction,
                  child: Padding(
                    padding: const EdgeInsets.only(bottom: 4),
                    child: Text(
                      action!,
                      style: const TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w500,
                        decoration: TextDecoration.underline,
                        decorationThickness: 1.5,
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

/// The big banner at the top: the admin's HERO banners, sliding by themselves,
/// with their words over the picture and the "Custom design" button beside.
class HomeHero extends ConsumerStatefulWidget {
  const HomeHero({super.key, required this.banners});
  final List<ShopBanner> banners;

  @override
  ConsumerState<HomeHero> createState() => _HomeHeroState();
}

class _HomeHeroState extends ConsumerState<HomeHero> {
  final _pages = PageController();
  Timer? _timer;
  int _index = 0;

  @override
  void initState() {
    super.initState();
    if (widget.banners.length > 1) {
      _timer = Timer.periodic(const Duration(seconds: 6), (_) {
        if (!_pages.hasClients) return;
        _pages.animateToPage(
          (_index + 1) % widget.banners.length,
          duration: const Duration(milliseconds: 600),
          curve: Curves.easeOut,
        );
      });
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    _pages.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final height = MediaQuery.sizeOf(context).height * 0.66;
    if (widget.banners.isEmpty) {
      // A shop with no hero banner: its name in words, no picture (as the website).
      return Container(
        // At least this tall, taller when the phone's text is large; black in both themes.
        constraints: BoxConstraints(minHeight: height * 0.6),
        color: AppColors.black,
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 36),
        alignment: Alignment.bottomLeft,
        child: const _HeroWords(
          label: 'Erezer',
          title: 'Clothing & custom t-shirts.',
          details: 'Shop the collection, or design your own.',
        ),
      );
    }
    return SizedBox(
      height: height,
      child: Stack(
        children: [
          PageView.builder(
            key: const Key('hero-banners'),
            controller: _pages,
            itemCount: widget.banners.length,
            onPageChanged: (i) => setState(() => _index = i),
            itemBuilder: (_, i) {
              final b = widget.banners[i];
              return Stack(
                fit: StackFit.expand,
                children: [
                  NetImage(url: b.mobileImageUrl ?? b.imageUrl, fallback: b.title ?? 'Erezer'),
                  const DecoratedBox(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.bottomCenter,
                        end: Alignment.topCenter,
                        colors: [Color(0xCC000000), Color(0x59000000), Color(0x1A000000)],
                      ),
                    ),
                  ),
                  Positioned(
                    left: 16,
                    right: 16,
                    bottom: 40,
                    child: _HeroWords(
                      label: b.title,
                      title: b.title,
                      details: b.details,
                      ctaLabel: b.ctaLabel,
                      onCta: () => openShopLink(context, ref, b.ctaLink ?? '/shop'),
                    ),
                  ),
                ],
              );
            },
          ),
          if (widget.banners.length > 1)
            Positioned(
              bottom: 14,
              left: 0,
              right: 0,
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  for (var i = 0; i < widget.banners.length; i++)
                    AnimatedContainer(
                      duration: const Duration(milliseconds: 300),
                      margin: const EdgeInsets.symmetric(horizontal: 4),
                      width: i == _index ? 32 : 8,
                      height: 8,
                      decoration: BoxDecoration(
                        color: i == _index ? Colors.white : Colors.white54,
                        borderRadius: BorderRadius.circular(99),
                      ),
                    ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

class _HeroWords extends StatelessWidget {
  const _HeroWords({this.label, this.title, this.details, this.ctaLabel, this.onCta});
  final String? label;
  final String? title;
  final String? details;
  final String? ctaLabel;
  final VoidCallback? onCta;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        if ((label ?? '').isNotEmpty) Eyebrow(label!, color: Colors.white70),
        if ((title ?? '').isNotEmpty) ...[
          const SizedBox(height: 8),
          Text(
            title!,
            style: const TextStyle(color: Colors.white, fontSize: 30, fontWeight: FontWeight.w600, height: 1.1),
          ),
        ],
        if ((details ?? '').isNotEmpty) ...[
          const SizedBox(height: 6),
          Text(details!, style: const TextStyle(color: Color(0xD9FFFFFF), fontSize: 15)),
        ],
        const SizedBox(height: 16),
        Wrap(
          spacing: 10,
          runSpacing: 10,
          children: [
            FilledButton(
              onPressed: onCta ?? () => openAllProducts(context),
              style: FilledButton.styleFrom(backgroundColor: Colors.white, foregroundColor: AppColors.black),
              child: Text(ctaLabel ?? 'Shop the collection'),
            ),
            OutlinedButton(
              key: const Key('hero-custom-design'),
              onPressed: () => openDesignStudio(context),
              style: OutlinedButton.styleFrom(
                foregroundColor: Colors.white,
                side: const BorderSide(color: Colors.white54),
                shape: const StadiumBorder(),
                padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 14),
              ),
              child: const Text('Custom design'),
            ),
          ],
        ),
      ],
    );
  }
}

/// A product in a two-column grid: photo, category, name, price and "Add to
/// Cart", which puts one in the cart at once (as on the website). After that
/// the button says "+ Add" and a "Checkout" button joins it.
class GridProductCard extends ConsumerStatefulWidget {
  const GridProductCard({super.key, required this.product});
  final Product product;

  @override
  ConsumerState<GridProductCard> createState() => _GridProductCardState();
}

class _GridProductCardState extends ConsumerState<GridProductCard> {
  bool _adding = false;

  /// This card's product was just put in the cart: offer the way to the checkout.
  bool _added = false;

  Future<void> _quickAdd() async {
    if (_adding) return;
    setState(() => _adding = true);
    final result = await addFromList(ref, widget.product.id);
    if (!mounted) return;
    setState(() {
      _adding = false;
      if (result == QuickAddResult.added) _added = true;
    });
    if (result == QuickAddResult.failed) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(content: Text('Couldn’t add it. Please check your internet and try again.')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final p = widget.product;
    final price = ref.watch(priceBookProvider).cardPrice(p);
    return Container(
      decoration: BoxDecoration(
        color: AppColors.card,
        border: Border.all(color: AppColors.line, width: 0.5),
      ),
      child: InkWell(
        key: Key('product-${p.id}'),
        onTap: () => openProduct(context, p.id),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            AspectRatio(
              aspectRatio: 1 / 1.25,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  NetImage(url: p.imageUrl, fallback: p.name),
                  Positioned(top: 8, right: 8, child: HeartButton(product: p)),
                  if (p.isOutOfStock)
                    Container(
                      color: AppColors.card.withValues(alpha: 0.55),
                      alignment: Alignment.center,
                      child: const Text('Sold out', style: TextStyle(fontWeight: FontWeight.w700)),
                    ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 12, 12, 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if ((p.categoryName ?? '').isNotEmpty)
                    Text(
                      p.categoryName!.toUpperCase(),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(fontSize: 10.5, letterSpacing: 1.2, color: AppColors.muted),
                    ),
                  const SizedBox(height: 6),
                  // Two lines' room even for a short name (at the phone's text size), so cards side by side line up.
                  SizedBox(
                    height: MediaQuery.textScalerOf(context).scale(14) * 1.35 * 2 + 2,
                    child: Text(
                      p.name,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 14, height: 1.35),
                    ),
                  ),
                  const SizedBox(height: 6),
                  Wrap(
                    crossAxisAlignment: WrapCrossAlignment.center,
                    spacing: 5,
                    children: [
                      Text(taka(price), style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14)),
                      if (p.price > price + 0.001)
                        Text(
                          taka(p.price),
                          style: TextStyle(
                            fontSize: 11.5,
                            color: AppColors.muted,
                            decoration: TextDecoration.lineThrough,
                          ),
                        ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  SizedBox(
                    height: 38,
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Expanded(
                          child: FilledButton(
                            key: Key('card-cart-${p.id}'),
                            onPressed: p.isOutOfStock || _adding ? null : _quickAdd,
                            style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 6)),
                            child: FittedBox(
                              fit: BoxFit.scaleDown,
                              child: Text(
                                p.isOutOfStock ? 'Sold out' : (_added ? '+ Add' : 'Add to Cart'),
                                style: const TextStyle(fontSize: 13),
                              ),
                            ),
                          ),
                        ),
                        if (_added) ...[
                          const SizedBox(width: 6),
                          Expanded(
                            child: FilledButton(
                              key: Key('card-checkout-${p.id}'),
                              onPressed: () => context.go('/cart/checkout'),
                              style: FilledButton.styleFrom(
                                backgroundColor: const Color(0xFF059669),
                                foregroundColor: Colors.white,
                                padding: const EdgeInsets.symmetric(horizontal: 6),
                              ),
                              child: const FittedBox(
                                fit: BoxFit.scaleDown,
                                child: Text('Checkout', style: TextStyle(fontSize: 13)),
                              ),
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Up to [max] products, two to a row, edge to edge inside the page margins.
class ProductGridBand extends StatelessWidget {
  const ProductGridBand({super.key, required this.products, this.max = 5});
  final List<Product> products;
  final int max;

  @override
  Widget build(BuildContext context) {
    final shown = products.take(max).toList();
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: LayoutBuilder(
        builder: (context, box) {
          final w = box.maxWidth / 2;
          return Wrap(
            children: [
              for (final p in shown)
                SizedBox(
                  width: w,
                  child: GridProductCard(product: p),
                ),
            ],
          );
        },
      ),
    );
  }
}

/// "COLLECTIONS / Shop by category": big picture tiles, two to a row, edge to edge.
class CollectionTiles extends StatelessWidget {
  const CollectionTiles({super.key, required this.categories});
  final List<Category> categories;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, box) {
        final w = box.maxWidth / 2;
        return Wrap(
          key: const Key('collection-tiles'),
          children: [
            for (final c in categories.take(6))
              SizedBox(
                width: w,
                height: w * 1.25,
                child: InkWell(
                  onTap: () => openCategory(context, c.id),
                  child: Stack(
                    fit: StackFit.expand,
                    children: [
                      if ((c.imageUrl ?? '').isNotEmpty)
                        NetImage(url: c.imageUrl, fallback: c.name)
                      else
                        // A category with no picture of its own gets a plain tile.
                        DecoratedBox(
                          decoration: BoxDecoration(
                            gradient: LinearGradient(
                              begin: Alignment.topLeft,
                              end: Alignment.bottomRight,
                              colors: [AppColors.muted, Color(0xFF171717)],
                            ),
                          ),
                        ),
                      const DecoratedBox(
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            begin: Alignment.bottomCenter,
                            end: Alignment.topCenter,
                            colors: [Color(0xBF000000), Color(0x26000000), Colors.transparent],
                          ),
                        ),
                      ),
                      Positioned(
                        left: 12,
                        right: 12,
                        bottom: 14,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Eyebrow('Collection', color: Colors.white70),
                            const SizedBox(height: 3),
                            Text(
                              c.name,
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 16,
                                fontWeight: FontWeight.w600,
                                height: 1.2,
                              ),
                            ),
                            const SizedBox(height: 6),
                            const Row(
                              children: [
                                Text(
                                  'Shop now',
                                  style: TextStyle(color: Colors.white, fontSize: 12.5, fontWeight: FontWeight.w500),
                                ),
                                SizedBox(width: 6),
                                Icon(Icons.arrow_forward, color: Colors.white, size: 16),
                              ],
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
          ],
        );
      },
    );
  }
}

/// The custom design promo: a grey panel with its words, then its picture.
class CustomDesignPromo extends ConsumerWidget {
  const CustomDesignPromo({super.key, required this.banner});
  final ShopBanner banner;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final b = banner;
    return Padding(
      padding: const EdgeInsets.only(top: 40),
      child: Column(
        key: const Key('custom-design-promo'),
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Container(
            color: AppColors.soft,
            padding: const EdgeInsets.fromLTRB(32, 56, 32, 56),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  ((b.title ?? '').isEmpty ? 'Customize your apparel, your way.' : b.title!).toUpperCase(),
                  style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w600, height: 1.2, letterSpacing: -0.4),
                ),
                const SizedBox(height: 18),
                Text(
                  (b.details ?? '').isEmpty
                      ? 'Design your own t-shirts, hoodies and more in our online studio — no minimum order, even a single piece.'
                      : b.details!,
                  style: TextStyle(color: AppColors.muted, height: 1.6),
                ),
                const SizedBox(height: 28),
                FilledButton(
                  onPressed: () => openShopLink(context, ref, b.ctaLink ?? '/custom-design'),
                  style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 16)),
                  child: Text(
                    (b.ctaLabel ?? 'Try it now').toUpperCase(),
                    style: const TextStyle(fontSize: 12.5, letterSpacing: 2.2),
                  ),
                ),
              ],
            ),
          ),
          SizedBox(
            height: MediaQuery.sizeOf(context).height * 0.46,
            child: NetImage(url: b.mobileImageUrl ?? b.imageUrl, fallback: 'Erezer'),
          ),
        ],
      ),
    );
  }
}

/// The admin's highlights: little cards, two to a row.
class HighlightsBand extends StatelessWidget {
  const HighlightsBand({super.key, required this.items});
  final List<Highlight> items;

  static IconData _icon(String name) => switch (name) {
    'truck' => Icons.local_shipping_outlined,
    'shield' => Icons.verified_user_outlined,
    'refresh' => Icons.autorenew,
    _ => Icons.star_border,
  };

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 56, 16, 0),
      child: LayoutBuilder(
        builder: (context, box) {
          final w = box.maxWidth / 2;
          return Wrap(
            key: const Key('highlights'),
            children: [
              for (final h in items)
                Container(
                  width: w,
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppColors.card,
                    border: Border.all(color: AppColors.line, width: 0.5),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Container(
                        width: 28,
                        height: 28,
                        decoration: BoxDecoration(color: AppColors.ink, shape: BoxShape.circle),
                        child: Icon(_icon(h.icon), size: 15, color: AppColors.onInk),
                      ),
                      const SizedBox(height: 10),
                      Text(h.value, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
                      const SizedBox(height: 3),
                      Text(
                        h.label.toUpperCase(),
                        style: TextStyle(fontSize: 9.5, letterSpacing: 1, color: AppColors.muted),
                      ),
                      const SizedBox(height: 2),
                      Text(h.description, style: TextStyle(fontSize: 11.5, color: AppColors.muted)),
                    ],
                  ),
                ),
            ],
          );
        },
      ),
    );
  }
}

/// The black strip of words that slides by ("PREMIUM MATERIALS ✦ ...").
class MarqueeStrip extends StatefulWidget {
  const MarqueeStrip({super.key, required this.items});
  final List<String> items;

  @override
  State<MarqueeStrip> createState() => _MarqueeStripState();
}

class _MarqueeStripState extends State<MarqueeStrip> with SingleTickerProviderStateMixin {
  late final _ticker = AnimationController(
    vsync: this,
    duration: Duration(seconds: 6 * widget.items.length),
  )..repeat();
  final _rowKey = GlobalKey();

  @override
  void dispose() {
    _ticker.dispose();
    super.dispose();
  }

  Widget _row({Key? key}) => Row(
    key: key,
    mainAxisSize: MainAxisSize.min,
    children: [
      for (final item in widget.items) ...[
        const SizedBox(width: 32),
        Text(
          item.toUpperCase(),
          style: const TextStyle(
            color: Color(0xE6FFFFFF),
            fontSize: 13,
            fontWeight: FontWeight.w600,
            letterSpacing: 2.8,
          ),
        ),
        const SizedBox(width: 28),
        const Icon(Icons.auto_awesome, size: 13, color: Color(0xCCFCD34D)),
      ],
    ],
  );

  @override
  Widget build(BuildContext context) {
    return Container(
      key: const Key('marquee'),
      margin: const EdgeInsets.only(top: 56),
      height: 52,
      decoration: const BoxDecoration(
        gradient: LinearGradient(colors: [Color(0xFF0A0A0A), Color(0xFF262626), Color(0xFF0A0A0A)]),
      ),
      child: ClipRect(
        child: AnimatedBuilder(
          animation: _ticker,
          builder: (context, child) {
            // Two copies side by side, moved left by one copy's width, loop forever.
            final width = (_rowKey.currentContext?.findRenderObject() as RenderBox?)?.size.width ?? 0;
            return OverflowBox(
              alignment: Alignment.centerLeft,
              maxWidth: double.infinity,
              child: Transform.translate(offset: Offset(-_ticker.value * width, 0), child: child),
            );
          },
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              _row(key: _rowKey),
              _row(),
            ],
          ),
        ),
      ),
    );
  }
}

/// "OUR STORY": the shop's words, button and social accounts, then its photos.
class OurStoryBand extends ConsumerWidget {
  const OurStoryBand({super.key, required this.story});
  final BrandStory story;

  static IconData _network(String? url) {
    final u = (url ?? '').toLowerCase();
    if (u.contains('instagram')) return Icons.camera_alt_outlined;
    if (u.contains('facebook')) return Icons.facebook;
    if (u.contains('tiktok')) return Icons.music_note_outlined;
    if (u.contains('youtube')) return Icons.smart_display_outlined;
    return Icons.link;
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = story;
    final photos = s.images;
    return Column(
      key: const Key('our-story'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(24, 64, 24, 40),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if ((s.eyebrow ?? '').isNotEmpty)
                Row(
                  children: [
                    Container(width: 40, height: 1, color: AppColors.muted),
                    const SizedBox(width: 12),
                    Eyebrow(s.eyebrow!),
                  ],
                ),
              if ((s.heading ?? '').isNotEmpty) ...[
                const SizedBox(height: 20),
                Text(
                  s.heading!,
                  style: const TextStyle(fontSize: 34, fontWeight: FontWeight.w600, height: 1.1, letterSpacing: -0.6),
                ),
              ],
              if ((s.body ?? '').isNotEmpty) ...[
                const SizedBox(height: 20),
                Text(s.body!, style: TextStyle(fontSize: 15.5, height: 1.65, color: AppColors.muted)),
              ],
              const SizedBox(height: 28),
              Wrap(
                spacing: 20,
                runSpacing: 14,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  if ((s.ctaLabel ?? '').isNotEmpty)
                    FilledButton(
                      onPressed: () => openShopLink(context, ref, s.ctaLink ?? '/shop'),
                      child: Text(s.ctaLabel!),
                    ),
                  for (final social in s.socials)
                    InkWell(
                      onTap: social.url == null ? null : () => openOutside(social.url!),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(_network(social.url), size: 16, color: AppColors.muted),
                          const SizedBox(width: 6),
                          Text(
                            social.handle,
                            style: TextStyle(color: AppColors.muted, fontWeight: FontWeight.w500),
                          ),
                        ],
                      ),
                    ),
                ],
              ),
            ],
          ),
        ),
        if (photos.isNotEmpty) _Mosaic(photos: photos),
      ],
    );
  }
}

/// The story photos: the first one large (two by two), the rest small, three to a row.
class _Mosaic extends StatelessWidget {
  const _Mosaic({required this.photos});
  final List<String> photos;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, box) {
        final cell = box.maxWidth / 3;
        Widget photo(String url, double size) => SizedBox(
          width: size,
          height: size,
          child: NetImage(url: url, fallback: 'E'),
        );
        final rest = photos.skip(3).toList();
        return Column(
          children: [
            Row(
              children: [
                photo(photos[0], cell * 2),
                Column(children: [for (final p in photos.skip(1).take(2)) photo(p, cell)]),
              ],
            ),
            Wrap(children: [for (final p in rest) photo(p, cell)]),
          ],
        );
      },
    );
  }
}

/// The tile grid: up to four banner pictures, two to a row, with their button word.
class TileGridBand extends ConsumerWidget {
  const TileGridBand({super.key, required this.banners});
  final List<ShopBanner> banners;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Padding(
      padding: const EdgeInsets.only(top: 40),
      child: LayoutBuilder(
        builder: (context, box) {
          final w = box.maxWidth / 2;
          return Wrap(
            children: [
              for (final b in banners)
                SizedBox(
                  key: Key('banner-${b.slot}'),
                  width: w,
                  height: 190,
                  child: InkWell(
                    onTap: () => openShopLink(context, ref, b.ctaLink ?? '/shop'),
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        NetImage(url: b.mobileImageUrl ?? b.imageUrl, fallback: b.title ?? 'E'),
                        Container(color: const Color(0x26000000)),
                        Center(
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                            decoration: BoxDecoration(
                              color: const Color(0x33000000),
                              border: Border.all(color: Colors.white70),
                              borderRadius: BorderRadius.circular(999),
                            ),
                            child: Text(
                              (b.ctaLabel ?? b.title ?? '').toUpperCase(),
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 10.5,
                                fontWeight: FontWeight.w600,
                                letterSpacing: 1.3,
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }
}

/// The split band: up to two tall pictures with their words in the middle.
class SplitBand extends ConsumerWidget {
  const SplitBand({super.key, required this.banners});
  final List<ShopBanner> banners;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final height = MediaQuery.sizeOf(context).height * 0.7;
    return Padding(
      padding: const EdgeInsets.only(top: 40),
      child: Column(
        children: [
          for (final b in banners)
            SizedBox(
              key: Key('banner-${b.slot}'),
              height: height,
              child: InkWell(
                onTap: () => openShopLink(context, ref, b.ctaLink ?? '/shop'),
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    NetImage(url: b.mobileImageUrl ?? b.imageUrl, fallback: b.title ?? 'E'),
                    const DecoratedBox(
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          begin: Alignment.bottomCenter,
                          end: Alignment.topCenter,
                          colors: [Color(0xB3000000), Color(0x33000000), Color(0x1A000000)],
                        ),
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 24),
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          if ((b.title ?? '').isNotEmpty)
                            Text(
                              b.title!.toUpperCase(),
                              textAlign: TextAlign.center,
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 30,
                                fontWeight: FontWeight.w600,
                                letterSpacing: 3.5,
                              ),
                            ),
                          if ((b.details ?? '').isNotEmpty) ...[
                            const SizedBox(height: 12),
                            Text(
                              b.details!,
                              textAlign: TextAlign.center,
                              style: const TextStyle(color: Colors.white70),
                            ),
                          ],
                          if ((b.ctaLabel ?? '').isNotEmpty) ...[
                            const SizedBox(height: 28),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 12),
                              decoration: BoxDecoration(
                                border: Border.all(color: Colors.white70),
                                borderRadius: BorderRadius.circular(999),
                              ),
                              child: Text(
                                b.ctaLabel!.toUpperCase(),
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 12,
                                  fontWeight: FontWeight.w600,
                                  letterSpacing: 2.2,
                                ),
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}

/// A running flash sale: a dark headline with its offer, a countdown, and its products.
class FlashSaleBand extends StatefulWidget {
  const FlashSaleBand({super.key, required this.sale});
  final FlashSale sale;

  @override
  State<FlashSaleBand> createState() => _FlashSaleBandState();
}

class _FlashSaleBandState extends State<FlashSaleBand> {
  late final Timer _tick = Timer.periodic(const Duration(seconds: 1), (_) => setState(() {}));

  @override
  void dispose() {
    _tick.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.sale;
    final left = s.endsAt.difference(DateTime.now());
    if (left.isNegative) return const SizedBox.shrink();
    String two(int n) => n.toString().padLeft(2, '0');
    final clock = '${two(left.inHours)}:${two(left.inMinutes % 60)}:${two(left.inSeconds % 60)}';
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 40, 16, 0),
      child: Container(
        key: const Key('flash-sale'),
        decoration: BoxDecoration(color: AppColors.black, borderRadius: BorderRadius.circular(20)),
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Eyebrow(s.label ?? 'Exclusive offer', color: Colors.white60),
            const SizedBox(height: 8),
            Text(
              s.name.toUpperCase(),
              style: const TextStyle(color: Colors.white, fontSize: 26, fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(color: const Color(0xFFDC2626), borderRadius: BorderRadius.circular(6)),
                  child: Text(
                    s.badge,
                    style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 12),
                  ),
                ),
                const Spacer(),
                const Text('ENDS IN ', style: TextStyle(color: Colors.white60, fontSize: 12)),
                Text(
                  clock,
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.w700,
                    fontFeatures: [FontFeature.tabularFigures()],
                  ),
                ),
              ],
            ),
            if (s.couponCode != null) ...[
              const SizedBox(height: 10),
              Text('Use code ${s.couponCode} at checkout', style: const TextStyle(color: Colors.white70)),
            ],
            const SizedBox(height: 16),
            ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: LayoutBuilder(
                builder: (context, box) => Wrap(
                  children: [
                    for (final p in s.products.take(4))
                      SizedBox(
                        width: box.maxWidth / 2,
                        child: GridProductCard(product: p),
                      ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// The shop's promises and its stores (the website's footer).
class FooterBand extends ConsumerWidget {
  const FooterBand({super.key, required this.promises, required this.outlets});
  final List<FooterPromise> promises;
  final List<ShopOutlet> outlets;

  static IconData _icon(String name) => switch (name) {
    'quality' => Icons.verified_outlined,
    'support' => Icons.chat_bubble_outline,
    'delivery' => Icons.local_shipping_outlined,
    _ => Icons.check_circle_outline,
  };

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Container(
      margin: const EdgeInsets.only(top: 40),
      decoration: BoxDecoration(
        border: Border(top: BorderSide(color: AppColors.line)),
      ),
      padding: const EdgeInsets.fromLTRB(16, 32, 16, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (final p in promises)
            Padding(
              padding: const EdgeInsets.only(bottom: 22),
              child: Row(
                children: [
                  Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(color: AppColors.ink, shape: BoxShape.circle),
                    child: Icon(_icon(p.icon), color: AppColors.onInk, size: 19),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(p.title, style: const TextStyle(fontWeight: FontWeight.w600)),
                        const SizedBox(height: 2),
                        Text(p.description, style: TextStyle(color: AppColors.muted, fontSize: 13)),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          if (outlets.isNotEmpty) ...[
            const SizedBox(height: 16),
            const Eyebrow('Our store locations'),
            const SizedBox(height: 16),
            for (final o in outlets)
              Padding(
                padding: const EdgeInsets.only(bottom: 16),
                child: Card(
                  key: const Key('outlet'),
                  clipBehavior: Clip.antiAlias,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if ((o.imageUrl ?? '').isNotEmpty)
                        AspectRatio(
                          aspectRatio: 16 / 10,
                          child: NetImage(url: o.imageUrl, fallback: o.name),
                        ),
                      Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(o.name, style: const TextStyle(fontWeight: FontWeight.w700)),
                            if ((o.address ?? '').isNotEmpty) ...[
                              const SizedBox(height: 4),
                              Text(o.address!, style: TextStyle(color: AppColors.muted, fontSize: 13)),
                              const SizedBox(height: 8),
                              InkWell(
                                onTap: () => openOutside(
                                  o.mapUrl ??
                                      'https://www.google.com/maps/search/?api=1&query=${Uri.encodeQueryComponent(o.address!)}',
                                ),
                                child: const Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Icon(Icons.place_outlined, size: 16),
                                    SizedBox(width: 4),
                                    Text('View on map', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
                                  ],
                                ),
                              ),
                            ],
                            if ((o.phone ?? '').isNotEmpty) ...[
                              const SizedBox(height: 8),
                              InkWell(
                                onTap: () => openOutside('tel:${o.phone!.replaceAll(RegExp(r'[^0-9+]'), '')}'),
                                child: Text(
                                  o.phone!,
                                  style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
          ],
        ],
      ),
    );
  }
}
