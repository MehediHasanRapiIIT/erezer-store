import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../app/theme.dart';
import '../data/cart.dart';

/// The bottom tab bar around every main page.
class AppShell extends ConsumerWidget {
  const AppShell({super.key, required this.shell});
  final StatefulNavigationShell shell;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final inCart = ref.watch(cartCountProvider);
    return Scaffold(
      // Pages show their messages above their own bottom buttons (Add to cart,
      // Place order) instead of over them, inside the tab bar.
      body: ScaffoldMessenger(child: shell),
      bottomNavigationBar: DecoratedBox(
        decoration: BoxDecoration(
          border: Border(top: BorderSide(color: AppColors.line)),
        ),
        child: NavigationBar(
          selectedIndex: shell.currentIndex,
          // Tapping the open tab again goes back to that tab's first page.
          onDestinationSelected: (i) => shell.goBranch(i, initialLocation: i == shell.currentIndex),
          destinations: [
            const NavigationDestination(icon: Icon(Icons.home_outlined), selectedIcon: Icon(Icons.home), label: 'Home'),
            const NavigationDestination(
              icon: Icon(Icons.grid_view_outlined),
              selectedIcon: Icon(Icons.grid_view),
              label: 'Categories',
            ),
            NavigationDestination(
              icon: Badge(
                isLabelVisible: inCart > 0,
                label: Text('$inCart', key: const Key('cart-badge')),
                child: const Icon(Icons.shopping_bag_outlined),
              ),
              selectedIcon: Badge(
                isLabelVisible: inCart > 0,
                label: Text('$inCart'),
                child: const Icon(Icons.shopping_bag),
              ),
              label: 'Cart',
            ),
            const NavigationDestination(
              icon: Icon(Icons.person_outline),
              selectedIcon: Icon(Icons.person),
              label: 'Account',
            ),
          ],
        ),
      ),
    );
  }
}
