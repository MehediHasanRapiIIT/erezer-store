import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../app/nav.dart';
import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../data/models.dart';
import '../../widgets/common.dart';
import '../../widgets/product_card.dart';

/// First page: the shop's banners, categories, new arrivals, popular and
/// featured products (all set in the admin panel), and the help line.
class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final home = ref.watch(homeProvider);
    final categories = ref.watch(categoriesProvider).value ?? const [];
    final settings = ref.watch(storeSettingsProvider).value;
    final top = categories.where((c) => c.parentId == null).toList()
      ..sort((a, b) => a.shopOrder.compareTo(b.shopOrder));

    return Scaffold(
      appBar: AppBar(
        title: Image.asset('assets/images/wordmark.png', height: 16),
        actions: [
          IconButton(
            key: const Key('home-search'),
            icon: const Icon(Icons.search),
            tooltip: 'Search',
            onPressed: () => openSearch(context),
          ),
        ],
      ),
      body: home.when(
        loading: () => const Center(child: CircularProgressIndicator(color: AppColors.ink)),
        error: (e, _) => ErrorRetry(
          message: e.toString(),
          onRetry: () {
            ref.invalidate(homeProvider);
            ref.invalidate(categoriesProvider);
          },
        ),
        data: (data) {
          final hero = data.banners.where((b) => b.slot == 'HERO').toList();
          final tiles = data.banners.where((b) => b.slot != 'HERO').toList();
          return RefreshIndicator(
            color: AppColors.ink,
            onRefresh: () async {
              ref.invalidate(storeSettingsProvider);
              ref.invalidate(categoriesProvider);
              ref.invalidate(homeProvider);
              await ref.read(homeProvider.future);
            },
            child: ListView(
              padding: const EdgeInsets.only(bottom: 24),
              children: [
                if (hero.isNotEmpty) _HeroBanners(banners: hero) else _Welcome(onShop: () => context.go('/categories')),
                if (top.isNotEmpty) ...[
                  const _SectionTitle('Shop by category'),
                  SizedBox(
                    height: 132,
                    child: ListView.separated(
                      key: const Key('home-categories'),
                      scrollDirection: Axis.horizontal,
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      itemCount: top.length,
                      separatorBuilder: (_, _) => const SizedBox(width: 12),
                      itemBuilder: (context, i) => SizedBox(
                        width: 92,
                        child: InkWell(
                          onTap: () => openCategory(context, top[i].id),
                          child: Column(
                            children: [
                              ClipOval(
                                child: SizedBox(
                                  width: 84,
                                  height: 84,
                                  child: NetImage(url: top[i].imageUrl, fallback: top[i].name),
                                ),
                              ),
                              const SizedBox(height: 6),
                              Text(
                                top[i].name,
                                maxLines: 2,
                                textAlign: TextAlign.center,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(fontSize: 12.5, fontWeight: FontWeight.w500, height: 1.2),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                ],
                if (data.newArrivals.isNotEmpty) ...[
                  const _SectionTitle('New arrivals'),
                  ProductRow(key: const Key('new-arrivals'), products: data.newArrivals),
                ],
                for (final s in data.sections)
                  if (s.products.isNotEmpty) ...[
                    _SectionTitle(s.name, onMore: () => openCategory(context, s.categoryId)),
                    ProductRow(products: s.products),
                  ],
                if (tiles.isNotEmpty) ...[const SizedBox(height: 8), for (final b in tiles) _BannerTile(banner: b)],
                if (data.popular.isNotEmpty) ...[
                  const _SectionTitle('Popular right now'),
                  ProductRow(products: data.popular),
                ],
                if (data.featured.isNotEmpty) ...[const _SectionTitle('Featured'), ProductRow(products: data.featured)],
                if (settings?.supportPhone != null)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 24, 16, 0),
                    child: Card(
                      child: ListTile(
                        leading: const Icon(Icons.support_agent),
                        title: Text('Need help? ${settings!.supportPhone}'),
                        subtitle: settings.supportHours == null ? null : Text(settings.supportHours!),
                      ),
                    ),
                  ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle(this.text, {this.onMore});
  final String text;
  final VoidCallback? onMore;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 24, 8, 12),
      child: Row(
        children: [
          Expanded(
            child: Text(text, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
          ),
          if (onMore != null)
            TextButton(
              onPressed: onMore,
              style: TextButton.styleFrom(foregroundColor: AppColors.ink),
              child: const Text('See all'),
            ),
        ],
      ),
    );
  }
}

/// The admin's HERO banners, sliding by themselves every few seconds.
class _HeroBanners extends ConsumerStatefulWidget {
  const _HeroBanners({required this.banners});
  final List<ShopBanner> banners;

  @override
  ConsumerState<_HeroBanners> createState() => _HeroBannersState();
}

class _HeroBannersState extends ConsumerState<_HeroBanners> {
  final _pages = PageController();
  Timer? _timer;
  int _index = 0;

  @override
  void initState() {
    super.initState();
    if (widget.banners.length > 1) {
      _timer = Timer.periodic(const Duration(seconds: 5), (_) {
        if (!_pages.hasClients) return;
        _pages.animateToPage(
          (_index + 1) % widget.banners.length,
          duration: const Duration(milliseconds: 500),
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
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(20),
        child: AspectRatio(
          aspectRatio: 4 / 5,
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
                            begin: Alignment.center,
                            end: Alignment.bottomCenter,
                            colors: [Colors.transparent, Color(0xAA000000)],
                          ),
                        ),
                      ),
                      Positioned(
                        left: 20,
                        right: 20,
                        bottom: 36,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            if ((b.title ?? '').isNotEmpty)
                              Text(
                                b.title!,
                                style: const TextStyle(color: Colors.white, fontSize: 24, fontWeight: FontWeight.w700),
                              ),
                            if (b.ctaLabel != null && b.ctaLink != null) ...[
                              const SizedBox(height: 12),
                              FilledButton(
                                onPressed: () => openShopLink(context, ref, b.ctaLink),
                                style: FilledButton.styleFrom(
                                  backgroundColor: Colors.white,
                                  foregroundColor: AppColors.ink,
                                ),
                                child: Text(b.ctaLabel!),
                              ),
                            ],
                          ],
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
                          duration: const Duration(milliseconds: 200),
                          margin: const EdgeInsets.symmetric(horizontal: 3),
                          width: i == _index ? 18 : 7,
                          height: 7,
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
        ),
      ),
    );
  }
}

/// A smaller banner (the website's grid and promo banners), tappable.
class _BannerTile extends ConsumerWidget {
  const _BannerTile({required this.banner});
  final ShopBanner banner;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final b = banner;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: () => openShopLink(context, ref, b.ctaLink),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(18),
          child: AspectRatio(
            aspectRatio: 16 / 10,
            child: Stack(
              fit: StackFit.expand,
              children: [
                NetImage(url: b.mobileImageUrl ?? b.imageUrl, fallback: b.title ?? 'Erezer'),
                if ((b.title ?? '').isNotEmpty || b.ctaLabel != null)
                  Positioned(
                    left: 16,
                    bottom: 16,
                    right: 16,
                    child: Row(
                      children: [
                        if ((b.title ?? '').isNotEmpty)
                          Expanded(
                            child: Text(
                              b.title!,
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 18,
                                fontWeight: FontWeight.w700,
                                shadows: [Shadow(blurRadius: 8, color: Colors.black54)],
                              ),
                            ),
                          )
                        else
                          const Spacer(),
                        if (b.ctaLabel != null)
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                            decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(999)),
                            child: Text(b.ctaLabel!, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 12)),
                          ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Welcome extends StatelessWidget {
  const _Welcome({required this.onShop});
  final VoidCallback onShop;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        gradient: const LinearGradient(colors: [Color(0xFF0A0A0A), Color(0xFF262626)]),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Image.asset('assets/images/logo_white.png', height: 32),
          const SizedBox(height: 16),
          const Text(
            'Welcome to Erezer',
            style: TextStyle(color: Colors.white, fontSize: 24, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 6),
          const Text('T-shirts, hoodies and your own designs.', style: TextStyle(color: Color(0xFFD4D4D4))),
          const SizedBox(height: 18),
          FilledButton(
            onPressed: onShop,
            style: FilledButton.styleFrom(backgroundColor: Colors.white, foregroundColor: AppColors.ink),
            child: const Text('Shop now'),
          ),
        ],
      ),
    );
  }
}
