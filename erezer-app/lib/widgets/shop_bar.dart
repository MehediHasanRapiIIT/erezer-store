import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../app/nav.dart';
import '../app/theme.dart';
import '../data/api_client.dart';
import '../data/auth.dart';
import '../data/cart.dart';
import '../data/models.dart';
import '../data/theme_mode.dart';
import '../data/wishlist.dart';

/// The website's top bar: menu, the Erezer name, search, wishlist and cart.
/// The page using it puts [ShopMenu] in its Scaffold's endDrawer.
class ShopAppBar extends ConsumerWidget implements PreferredSizeWidget {
  const ShopAppBar({super.key, this.title});

  /// Words in place of the Erezer name (e.g. "Categories").
  final String? title;

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final inCart = ref.watch(cartCountProvider);
    final saved = ref.watch(wishlistProvider).length;
    Widget counted(int n, IconData icon) => Badge(
      isLabelVisible: n > 0,
      label: Text('$n'),
      backgroundColor: AppColors.ink,
      textColor: AppColors.onInk,
      child: Icon(icon),
    );
    return AppBar(
      leading: IconButton(
        key: const Key('open-menu'),
        icon: const Icon(Icons.menu),
        tooltip: 'Menu',
        onPressed: () => Scaffold.of(context).openEndDrawer(),
      ),
      centerTitle: false,
      titleSpacing: 0,
      title: title != null
          ? Text(title!, style: const TextStyle(fontWeight: FontWeight.w600))
          : Image.asset('assets/images/wordmark.png', height: 15, color: AppColors.ink),
      actions: [
        IconButton(
          key: const Key('home-search'),
          icon: const Icon(Icons.search),
          tooltip: 'Search',
          onPressed: () => openSearch(context),
        ),
        IconButton(
          key: const Key('open-wishlist'),
          icon: counted(saved, Icons.favorite_border),
          tooltip: 'Wishlist',
          onPressed: () => context.push('/wishlist'),
        ),
        IconButton(
          key: const Key('open-cart'),
          icon: counted(inCart, Icons.shopping_bag_outlined),
          tooltip: 'Cart',
          onPressed: () => context.go('/cart'),
        ),
        const SizedBox(width: 4),
      ],
    );
  }
}

/// The website's phone menu, sliding in from the right: the shop's pages,
/// sign in or out, and day or dark.
class ShopMenu extends ConsumerWidget {
  const ShopMenu({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(authProvider);
    final sale = ref.watch(flashSaleProvider).value;
    final mode = ref.watch(themeModeProvider);
    final dark =
        mode == ThemeMode.dark || (mode == ThemeMode.system && Theme.of(context).brightness == Brightness.dark);

    // Closes the menu, then goes where the link points.
    void go(void Function() open) {
      Navigator.of(context).pop();
      open();
    }

    Widget link(String label, IconData icon, void Function() open, {Key? key}) => ListTile(
      key: key,
      leading: Icon(icon, color: AppColors.ink),
      title: Text(label, style: const TextStyle(fontSize: 16)),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      onTap: () => go(open),
    );

    return Drawer(
      key: const Key('shop-menu'),
      width: MediaQuery.sizeOf(context).width * 0.82,
      shape: const RoundedRectangleBorder(),
      child: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 8, 8, 8),
              child: Row(
                children: [
                  Image.asset('assets/images/wordmark.png', height: 15, color: AppColors.ink),
                  const Spacer(),
                  IconButton(
                    icon: const Icon(Icons.close),
                    tooltip: 'Close menu',
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ],
              ),
            ),
            Divider(height: 1, color: AppColors.line),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                children: [
                  link('Categories', Icons.grid_view_outlined, () => context.go('/categories')),
                  link('Shop', Icons.storefront_outlined, () => openAllProducts(context), key: const Key('menu-shop')),
                  link('Custom design', Icons.brush_outlined, () => openDesignStudio(context)),
                  if (sale != null) link('Flash sale', Icons.bolt_outlined, () => context.push('/flash-sale')),
                  link(
                    'Wishlist',
                    Icons.favorite_border,
                    () => context.push('/wishlist'),
                    key: const Key('menu-wishlist'),
                  ),
                  if (session != null) link('Orders', Icons.receipt_long_outlined, () => context.go('/account/orders')),
                  if (session != null) link('Account', Icons.person_outline, () => context.go('/account')),
                  link('Track order', Icons.local_shipping_outlined, () => context.go('/account/track')),
                ],
              ),
            ),
            Divider(height: 1, color: AppColors.line),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 16),
              child: Column(
                children: [
                  SizedBox(
                    width: double.infinity,
                    height: 48,
                    child: session != null
                        ? OutlinedButton(
                            onPressed: () => go(() => ref.read(authProvider.notifier).logout()),
                            style: OutlinedButton.styleFrom(shape: const StadiumBorder()),
                            child: const Text('Log out'),
                          )
                        : FilledButton(
                            onPressed: () => go(() => context.push('/account/login')),
                            child: const Text('Sign in'),
                          ),
                  ),
                  const SizedBox(height: 14),
                  Row(
                    children: [
                      Text('Theme', style: TextStyle(color: AppColors.muted)),
                      const Spacer(),
                      SegmentedButton<bool>(
                        key: const Key('theme-switch'),
                        showSelectedIcon: false,
                        segments: const [
                          ButtonSegment(
                            value: false,
                            icon: Icon(Icons.light_mode_outlined, size: 18),
                            label: Text('Day'),
                          ),
                          ButtonSegment(
                            value: true,
                            icon: Icon(Icons.dark_mode_outlined, size: 18),
                            label: Text('Dark'),
                          ),
                        ],
                        selected: {dark},
                        onSelectionChanged: (s) =>
                            ref.read(themeModeProvider.notifier).set(s.first ? ThemeMode.dark : ThemeMode.light),
                      ),
                    ],
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

/// The ♡ on a product: saves it to the wishlist, or takes it out.
class HeartButton extends ConsumerWidget {
  const HeartButton({super.key, required this.product, this.size = 34});
  final Product product;
  final double size;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final saved = ref.watch(wishlistProvider).any((p) => p.id == product.id);
    return Material(
      color: AppColors.card,
      shape: const CircleBorder(),
      elevation: 1,
      child: InkWell(
        key: Key('heart-${product.id}'),
        customBorder: const CircleBorder(),
        onTap: () {
          final added = ref.read(wishlistProvider.notifier).toggle(product);
          ScaffoldMessenger.of(context)
            ..hideCurrentSnackBar()
            ..showSnackBar(
              SnackBar(
                duration: const Duration(seconds: 2),
                content: Text(added ? 'Saved to your wishlist' : 'Removed from your wishlist'),
              ),
            );
        },
        child: SizedBox(
          width: size,
          height: size,
          child: Icon(
            saved ? Icons.favorite : Icons.favorite_border,
            size: size * 0.55,
            color: saved ? const Color(0xFFDC2626) : AppColors.ink,
          ),
        ),
      ),
    );
  }
}
