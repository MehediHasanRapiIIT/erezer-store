import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../app/nav.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../data/models.dart';
import '../../data/recently_viewed.dart';
import '../../widgets/common.dart';
import '../../widgets/product_card.dart';
import '../../widgets/shop_bar.dart';
import 'home_bands.dart';

/// The website's home page bands in its own default order, for a shop without a layout.
const defaultHomeLayout = [
  'SPLIT_BAND',
  'TILE_GRID',
  'CUSTOM_DESIGN_PROMO',
  'FLASH_SALE',
  'SHOP_BY_CATEGORY',
  'HIGHLIGHTS',
  'NEW_ARRIVALS',
  'FEATURED_PRODUCTS',
  'CATEGORY_COLLECTIONS',
  'MARQUEE',
  'RECENTLY_VIEWED',
  'OUR_STORY',
];

/// The home page, the same as the website's on a phone: the big banner, then
/// the bands the admin switched on in Admin → Home page layout, in their order,
/// and the shop's promises and stores at the foot.
class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final home = ref.watch(homeProvider);
    final categories = ref.watch(categoriesProvider).value ?? const [];
    final settings = ref.watch(storeSettingsProvider).value;
    final flashSale = ref.watch(flashSaleProvider).value;
    final recent = ref.watch(recentlyViewedProvider);
    // Main categories, as the website's "Shop by category" shows them.
    final top = categories.where((c) => c.parentId == null).toList()
      ..sort((a, b) => a.shopOrder.compareTo(b.shopOrder));

    return Scaffold(
      appBar: const ShopAppBar(),
      endDrawer: const ShopMenu(),
      body: home.when(
        loading: () => Center(child: CircularProgressIndicator(color: AppColors.ink)),
        error: (e, _) => ErrorRetry(
          message: e.toString(),
          onRetry: () {
            ref.invalidate(homeProvider);
            ref.invalidate(categoriesProvider);
          },
        ),
        data: (data) {
          List<ShopBanner> banners(List<String> slots) => [
            for (final slot in slots) ?data.banners.where((b) => b.slot == slot).firstOrNull,
          ];
          final hero = data.banners.where((b) => b.slot == 'HERO').toList();

          // Each band of the website's home page, by its layout name.
          List<Widget> band(String key) => switch (key) {
            'NEW_ARRIVALS' when data.newArrivals.isNotEmpty => [
              BandHeader(
                eyebrow: 'Just dropped',
                title: 'New arrivals',
                action: 'View all products',
                onAction: () => openAllProducts(context),
              ),
              ProductGridBand(key: const Key('new-arrivals'), products: data.newArrivals),
            ],
            'SHOP_BY_CATEGORY' when top.isNotEmpty => [
              BandHeader(
                eyebrow: 'Collections',
                title: 'Shop by category',
                action: 'Browse all',
                onAction: () => openCategoriesTab(context),
              ),
              CollectionTiles(categories: top),
            ],
            'TILE_GRID' => [
              if (banners(['GRID_1', 'GRID_2', 'GRID_3', 'GRID_4']) case final tiles when tiles.isNotEmpty)
                TileGridBand(banners: tiles),
            ],
            'SPLIT_BAND' => [
              if (banners(['SPLIT_LEFT', 'SPLIT_RIGHT']) case final panels when panels.isNotEmpty)
                SplitBand(banners: panels),
            ],
            'CUSTOM_DESIGN_PROMO' => [
              if (banners(['CUSTOM_PROMO']).firstOrNull case final promo?) CustomDesignPromo(banner: promo),
            ],
            'FLASH_SALE' => [if (flashSale != null) FlashSaleBand(sale: flashSale)],
            'HIGHLIGHTS' => [
              if (settings != null && settings.highlights.isNotEmpty) HighlightsBand(items: settings.highlights),
            ],
            'FEATURED_PRODUCTS' when data.featured.isNotEmpty => [
              BandHeader(
                title: 'Featured products',
                action: 'View all products',
                onAction: () => openAllProducts(context),
              ),
              ProductGridBand(products: data.featured),
            ],
            'CATEGORY_COLLECTIONS' => [
              for (final s in data.sections)
                if (s.products.isNotEmpty) ...[
                  BandHeader(
                    eyebrow: 'Collection',
                    title: s.name,
                    action: 'View all',
                    onAction: () => openCategory(context, s.categoryId),
                  ),
                  ProductGridBand(products: s.products, max: 10),
                ],
            ],
            'MARQUEE' => [if (settings != null && settings.marquee.isNotEmpty) MarqueeStrip(items: settings.marquee)],
            'RECENTLY_VIEWED' when recent.isNotEmpty => [
              const BandHeader(title: 'Recently viewed'),
              ProductRow(products: recent.take(6).toList()),
            ],
            'OUR_STORY' => [if (settings?.story case final story?) OurStoryBand(story: story)],
            _ => const [],
          };

          return RefreshIndicator(
            color: AppColors.ink,
            onRefresh: () async {
              ref.invalidate(storeSettingsProvider);
              ref.invalidate(categoriesProvider);
              ref.invalidate(flashSaleProvider);
              ref.invalidate(homeProvider);
              await ref.read(homeProvider.future);
            },
            child: ListView(
              key: const Key('home-list'),
              padding: const EdgeInsets.only(bottom: 24),
              children: [
                HomeHero(banners: hero),
                for (final key in settings?.homeLayout ?? defaultHomeLayout) ...band(key),
                if (settings != null && (settings.promises.isNotEmpty || settings.outlets.isNotEmpty))
                  FooterBand(promises: settings.promises, outlets: settings.outlets),
              ],
            ),
          );
        },
      ),
    );
  }
}
