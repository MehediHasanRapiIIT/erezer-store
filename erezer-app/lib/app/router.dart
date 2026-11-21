import 'package:go_router/go_router.dart';

import '../features/account/account_screen.dart';
import '../features/account/delete_account_screen.dart';
import '../features/account/login_screen.dart';
import '../features/account/order_detail_screen.dart';
import '../features/account/orders_screen.dart';
import '../features/account/profile_screen.dart';
import '../features/account/track_order_screen.dart';
import '../features/cart/cart_screen.dart';
import '../features/categories/categories_screen.dart';
import '../features/checkout/bkash_pay_screen.dart';
import '../features/checkout/checkout_screen.dart';
import '../features/checkout/order_placed_screen.dart';
import '../features/design/design_studio_screen.dart';
import '../features/home/home_screen.dart';
import '../features/product/product_screen.dart';
import '../features/products/product_list_screen.dart';
import '../features/search/search_screen.dart';
import '../features/wishlist/wishlist_screen.dart';
import '../data/models.dart';
import '../widgets/app_shell.dart';

/// Product (p/12) and category (c/20) pages, opened inside a tab.
List<RouteBase> _shopPages() => [
  GoRoute(
    path: 'p/:id',
    builder: (context, state) => ProductScreen(productId: int.parse(state.pathParameters['id']!)),
  ),
  GoRoute(
    path: 'c/:id',
    // c/all: every product (the website's /shop).
    builder: (context, state) => ProductListScreen(categoryId: int.tryParse(state.pathParameters['id']!)),
  ),
];

/// The four bottom tabs. Each tab keeps its own page history, so switching
/// tabs and back returns the customer to where they were.
GoRouter createRouter() => GoRouter(
  initialLocation: '/',
  routes: [
    StatefulShellRoute.indexedStack(
      builder: (context, state, shell) => AppShell(shell: shell),
      branches: [
        StatefulShellBranch(
          routes: [
            GoRoute(
              path: '/',
              builder: (context, state) => const HomeScreen(),
              routes: [
                ..._shopPages(),
                GoRoute(path: 'search', builder: (context, state) => const SearchScreen()),
                GoRoute(path: 'wishlist', builder: (context, state) => const WishlistScreen()),
                GoRoute(path: 'flash-sale', builder: (context, state) => const FlashSaleScreen()),
              ],
            ),
          ],
        ),
        StatefulShellBranch(
          routes: [
            GoRoute(path: '/categories', builder: (context, state) => const CategoriesScreen(), routes: _shopPages()),
          ],
        ),
        StatefulShellBranch(
          routes: [
            GoRoute(
              path: '/cart',
              builder: (context, state) => const CartScreen(),
              routes: [
                ..._shopPages(),
                GoRoute(path: 'checkout', builder: (context, state) => const CheckoutScreen()),
              ],
            ),
          ],
        ),
        StatefulShellBranch(
          routes: [
            GoRoute(
              path: '/account',
              builder: (context, state) => const AccountScreen(),
              routes: [
                GoRoute(
                  path: 'login',
                  builder: (context, state) => LoginScreen(signUp: state.uri.queryParameters['signup'] == '1'),
                ),
                GoRoute(path: 'orders', builder: (context, state) => const OrdersScreen()),
                GoRoute(
                  path: 'orders/:id',
                  builder: (context, state) => OrderDetailScreen(orderId: state.pathParameters['id']!),
                ),
                GoRoute(
                  path: 'track',
                  builder: (context, state) => TrackOrderScreen(number: state.uri.queryParameters['number']),
                ),
                GoRoute(path: 'profile', builder: (context, state) => const ProfileScreen()),
                GoRoute(path: 'delete', builder: (context, state) => const DeleteAccountScreen()),
              ],
            ),
          ],
        ),
      ],
    ),
    // Paying and the thank-you page fill the screen, above the tabs, so the
    // Cart tab always opens the cart afterwards.
    GoRoute(path: '/design', builder: (context, state) => const DesignStudioScreen()),
    GoRoute(
      path: '/order/bkash',
      builder: (context, state) => BkashPayScreen(order: state.extra! as PlacedOrder),
    ),
    GoRoute(
      path: '/order/placed',
      builder: (context, state) => OrderPlacedScreen(order: state.extra as PlacedOrder?),
    ),
  ],
);
