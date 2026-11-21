import 'dart:convert';

import 'package:erezer_app/data/api_client.dart';
import 'package:erezer_app/main.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

Map<String, dynamic> _product(int id, String name, {double price = 1000, double? sale, int category = 1}) => {
  'id': id,
  'categoryId': category,
  'categoryName': 'T-Shirts',
  'name': name,
  'description': 'Soft cotton.',
  'price': price,
  'discountPrice': sale,
  'isAvailable': true,
  'stockStatus': 'IN_STOCK',
  'effectiveSizeChartId': 3,
};

/// The last order the app sent to the pretend server.
Map<String, dynamic>? _sentOrder;

/// A pretend server: two main categories, one with a sub-category, and a few products.
final _server = MockClient((req) async {
  final path = req.url.path;
  if (path == '/api/payments/bkash/init') {
    return http.Response(
      jsonEncode({'message': "bKash payment isn't available right now."}),
      400,
      headers: {'content-type': 'application/json; charset=utf-8'},
    );
  }
  if (path == '/app/consumer/guest/orders') {
    _sentOrder = jsonDecode(req.body) as Map<String, dynamic>;
    return http.Response(
      jsonEncode({'id': 'abc', 'orderNumber': 'EZ-123456', 'totalAmount': 1870}),
      201,
      headers: {'content-type': 'application/json; charset=utf-8'},
    );
  }
  final body = switch (path) {
    '/api/categories' => [
      {
        'id': 1,
        'name': 'T-Shirts',
        'isActive': true,
        'productCount': 12,
        'shopOrder': 1,
        'parentId': null,
        'slug': 't-shirts',
      },
      {
        'id': 2,
        'name': 'Hoodies',
        'isActive': true,
        'productCount': 5,
        'shopOrder': 2,
        'parentId': null,
        'slug': 'hoodies',
      },
      {'id': 3, 'name': 'Anime', 'isActive': true, 'productCount': 4, 'shopOrder': 1, 'parentId': 2, 'slug': 'anime'},
      {'id': 4, 'name': 'Hidden', 'isActive': false, 'productCount': 1, 'shopOrder': 3, 'parentId': null, 'slug': 'x'},
    ],
    '/api/store-settings' => {
      'supportPhone': '+880 1000',
      'supportHours': 'Sat–Thu',
      'paymentCodEnabled': true,
      'paymentBkashEnabled': true,
      'paymentCardEnabled': false,
      'couponsEnabled': false,
    },
    '/api/shipping/zones' => [
      {'id': 1, 'displayName': 'Inside Dhaka', 'flatFee': 70, 'isDefault': false},
      {'id': 2, 'displayName': 'Outside Dhaka', 'flatFee': 120, 'isDefault': true},
    ],
    '/api/checkout/quote' => () {
      final zone = (jsonDecode(req.body) as Map<String, dynamic>)['shippingZoneId'];
      final fee = zone == 1 ? 70 : 120;
      return {'subtotal': 1800, 'shippingFee': fee, 'discountAmount': 0, 'total': 1800 + fee, 'couponApplied': false};
    }(),
    '/api/discounts/active' => [
      {
        'id': 'd1',
        'scope': 'CATEGORY',
        'targetId': 2,
        'discountType': 'PERCENT',
        'discountValue': 10,
        'priority': 1,
        'stackable': false,
      },
    ],
    '/app/home' => {
      'banners': [],
      'newArrivalItems': [_product(10, 'New Tee', sale: 900)],
      'popularItems': [],
      'featuredItems': [],
      'homeSections': [],
    },
    '/api/products/browse' => {
      'content': req.url.queryParameters['q'] == 'zzz'
          ? []
          : [_product(10, 'New Tee', sale: 900), _product(11, 'Anime Hoodie', price: 2000, category: 3)],
      'totalElements': req.url.queryParameters['q'] == 'zzz' ? 0 : 2,
      'last': true,
    },
    '/api/products/11' => _product(11, 'Anime Hoodie', price: 2000, category: 3),
    '/api/products/11/variants' => [
      {'id': 1, 'size': 'M', 'stockQuantity': 5},
      {'id': 2, 'size': 'L', 'stockQuantity': 0},
    ],
    '/api/products/12' => _product(12, 'Two Fit Tee', price: 1000, sale: 900),
    '/api/products/12/variants' => [
      {
        'id': 21,
        'size': 'M',
        'stockQuantity': 5,
        'fit': 'DROP_SHOULDER',
        'fitLabel': 'Drop Shoulder',
        'priceOverride': 1200,
      },
      {'id': 22, 'size': 'L', 'stockQuantity': 5, 'fit': 'REGULAR_FIT', 'fitLabel': 'Regular Fit'},
    ],
    '/api/products/12/images' || '/api/products/12/related' => [],
    '/api/products/12/reviews' => {'content': [], 'totalElements': 0, 'last': true},
    '/api/products/11/images' => [],
    '/api/products/11/related' => [],
    '/api/products/11/reviews' => {'content': [], 'totalElements': 0, 'last': true},
    '/api/size-charts' => [
      {
        'id': 3,
        'name': 'Tees',
        'chart': {
          'columns': ['Chest'],
          'rows': [
            {
              'size': 'M',
              'cells': [
                {'cm': 101.6, 'inch': 40},
              ],
            },
          ],
        },
      },
    ],
    _ => null,
  };
  return body == null
      ? http.Response('not found', 404)
      : http.Response(jsonEncode(body), 200, headers: {'content-type': 'application/json; charset=utf-8'});
});

Future<void> _start(WidgetTester tester, {http.Client? client}) async {
  tester.view.physicalSize = const Size(1080, 2400);
  tester.view.devicePixelRatio = 2.6;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      retry: noRetry,
      overrides: [apiProvider.overrideWithValue(ApiClient(client: client ?? _server))],
      child: const ErezerApp(),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  setUpAll(() => GoogleFonts.config.allowRuntimeFetching = false);
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    _sentOrder = null;
  });

  testWidgets('home shows categories, new arrivals with sale prices, and the help line', (tester) async {
    await _start(tester);
    expect(find.text('Welcome to Erezer'), findsOneWidget);
    expect(find.text('T-Shirts'), findsOneWidget);
    expect(find.text('Hidden'), findsNothing);
    expect(find.text('New Tee'), findsOneWidget);
    expect(find.text('৳900'), findsOneWidget);
    expect(find.text('৳1,000'), findsOneWidget);
    await tester.drag(find.byType(Scrollable).first, const Offset(0, -800));
    await tester.pumpAndSettle();
    expect(find.text('Need help? +880 1000'), findsOneWidget);
  });

  testWidgets('a category lists its products with the category discount', (tester) async {
    await _start(tester);
    await tester.tap(find.text('Categories'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('design-your-own')), findsOneWidget);
    await tester.tap(find.text('Hoodies').last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Anime (4)'));
    await tester.pumpAndSettle();
    expect(find.text('2 products'), findsOneWidget);
    // Hoodies' 10% reaches Anime, its sub-category: ৳2,000 → ৳1,800.
    expect(find.text('৳1,800'), findsOneWidget);
    // New Tee's own sale (৳1,000 → ৳900) is 10% off too.
    expect(find.text('-10%'), findsNWidgets(2));
  });

  testWidgets('the product page picks a size, shows stock and the size chart', (tester) async {
    await _start(tester);
    await tester.tap(find.byKey(const Key('home-search')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('search-field')), 'hoodie');
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle();
    expect(find.text('2 results for "hoodie"'), findsOneWidget);

    await tester.tap(find.text('Anime Hoodie'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('product-name')), findsOneWidget);
    expect(find.text('In stock'), findsOneWidget);
    // M is in stock and picked; L is sold out.
    expect(find.text('Add to cart'), findsOneWidget);

    await tester.ensureVisible(find.byKey(const Key('size-chart-button')));
    await tester.tap(find.byKey(const Key('size-chart-button')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('size-chart')), findsOneWidget);
    expect(find.text('40'), findsOneWidget);
    await tester.tap(find.text('cm'));
    await tester.pumpAndSettle();
    expect(find.text('101.6'), findsOneWidget);
  });

  testWidgets('switching fit changes the sizes and the price', (tester) async {
    await _start(tester);
    final context = tester.element(find.byType(Scaffold).first);
    GoRouter.of(context).push('/p/12');
    await tester.pumpAndSettle();
    // Drop Shoulder's own ৳1,200 with the 10% sale: ৳1,080.
    expect(find.text('৳1,080'), findsOneWidget);
    await tester.scrollUntilVisible(find.byKey(const Key('size-M')), 200, scrollable: find.byType(Scrollable).last);
    expect(find.byKey(const Key('size-M')), findsOneWidget);

    await tester.ensureVisible(find.byKey(const Key('fit-REGULAR_FIT')));
    await tester.tap(find.byKey(const Key('fit-REGULAR_FIT')));
    await tester.pumpAndSettle();
    expect(find.text('৳900'), findsOneWidget);
    expect(find.byKey(const Key('size-M')), findsNothing);
    expect(find.byKey(const Key('size-L')), findsOneWidget);
    expect(find.text('Add to cart'), findsOneWidget);
  });

  testWidgets('a search with no results says so', (tester) async {
    await _start(tester);
    await tester.tap(find.byKey(const Key('home-search')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('search-field')), 'zzz');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();
    expect(find.text('Nothing found'), findsOneWidget);
  });

  testWidgets('add to cart, then check out with cash on delivery', (tester) async {
    await _start(tester);
    GoRouter.of(tester.element(find.byType(Scaffold).first)).push('/p/11');
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('add-to-cart')));
    await tester.pump();
    expect(find.text('Added to your cart'), findsOneWidget);
    await tester.tap(find.byKey(const Key('add-to-cart')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('cart-badge')), findsOneWidget);
    expect(find.text('2'), findsWidgets);

    await tester.tap(find.text('Cart'));
    await tester.pumpAndSettle();
    expect(find.text('Anime Hoodie'), findsOneWidget);
    // ৳2,000 less Hoodies' 10%, twice.
    expect(find.byKey(const Key('cart-subtotal')), findsOneWidget);
    expect(find.text('৳3,600'), findsWidgets);

    // Let the "Added to your cart" message fade first; it sits over the button.
    await tester.pump(const Duration(seconds: 5));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('go-checkout')));
    await tester.pumpAndSettle();
    // Outside Dhaka is the default area.
    expect(find.text('Place order · ৳1,920'), findsOneWidget);
    await tester.tap(find.byKey(const Key('zone-1')));
    await tester.pumpAndSettle();
    expect(find.text('Place order · ৳1,870'), findsOneWidget);
    // bKash can be chosen; card is off in this shop.
    expect(find.text('Pay now from your bKash account'), findsOneWidget);
    expect(find.byKey(const Key('pay-CARD')), findsNothing);

    // Nothing typed yet: the form says what is missing.
    await tester.tap(find.byKey(const Key('place-order')));
    await tester.pumpAndSettle();
    expect(find.text('Please fill this in'), findsWidgets);
    expect(_sentOrder, isNull);

    await tester.enterText(
      find.descendant(of: find.byKey(const Key('first-name')), matching: find.byType(TextField)),
      'Rahim',
    );
    await tester.enterText(
      find.descendant(of: find.byKey(const Key('last-name')), matching: find.byType(TextField)),
      'Uddin',
    );
    await tester.enterText(
      find.descendant(of: find.byKey(const Key('phone')), matching: find.byType(TextField)),
      '01712345678',
    );
    await tester.enterText(
      find.descendant(of: find.byKey(const Key('email')), matching: find.byType(TextField)),
      'rahim@example.com',
    );
    await tester.enterText(
      find.descendant(of: find.byKey(const Key('address')), matching: find.byType(TextField)),
      'House 1, Road 2, Dhanmondi',
    );
    await tester.tap(find.byKey(const Key('place-order')));
    await tester.pumpAndSettle();

    expect(_sentOrder?['paymentMethod'], 'CASH');
    expect(_sentOrder?['shippingZoneId'], 1);
    expect(_sentOrder?['items'], [
      {'productId': 11, 'variantId': 1, 'quantity': 2},
    ]);
    expect(find.byKey(const Key('order-number')), findsOneWidget);
    expect(find.text('EZ-123456'), findsOneWidget);
    expect(find.byKey(const Key('cart-badge')), findsNothing);

    // Afterwards the Cart tab opens the cart, not this page again.
    await tester.tap(find.byKey(const Key('continue-shopping')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cart'));
    await tester.pumpAndSettle();
    expect(find.text('Your cart is empty'), findsOneWidget);
  });

  testWidgets('bKash: the order is saved, and a payment that cannot start offers Pay again', (tester) async {
    SharedPreferences.setMockInitialValues({
      'erezer.cart.v1': jsonEncode([
        {'productId': 11, 'variantId': 1, 'quantity': 1, 'name': 'Anime Hoodie', 'unitPrice': 1800},
      ]),
      'erezer.details.v1': jsonEncode({
        'firstName': 'Rahim',
        'lastName': 'Uddin',
        'email': 'rahim@example.com',
        'phone': '01712345678',
        'address': 'House 1, Dhanmondi',
        'zoneId': 1,
      }),
    });
    await _start(tester);
    await tester.tap(find.text('Cart'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('go-checkout')));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(find.byKey(const Key('pay-BKASH')), 200, scrollable: find.byType(Scrollable).last);
    await tester.ensureVisible(find.byKey(const Key('pay-BKASH')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('pay-BKASH')));
    await tester.pumpAndSettle();
    expect(find.text('Pay with bKash · ৳1,870'), findsOneWidget);

    await tester.tap(find.byKey(const Key('place-order')));
    await tester.pumpAndSettle();
    expect(_sentOrder?['paymentMethod'], 'BKASH');
    expect(find.byKey(const Key('bkash-not-paid')), findsOneWidget);
    expect(find.text("bKash payment isn't available right now."), findsOneWidget);
    expect(find.textContaining('EZ-123456 is saved but not paid yet'), findsOneWidget);
    expect(find.byKey(const Key('bkash-pay-again')), findsOneWidget);
  });

  testWidgets('the cart and account tabs open', (tester) async {
    await _start(tester);
    await tester.tap(find.text('Cart'));
    await tester.pumpAndSettle();
    expect(find.text('Your cart is empty'), findsOneWidget);
    await tester.tap(find.text('Account'));
    await tester.pumpAndSettle();
    expect(find.text('Your Erezer account'), findsOneWidget);
  });

  testWidgets('a server problem offers Try again', (tester) async {
    await _start(tester, client: MockClient((_) async => http.Response('', 500)));
    expect(find.text('Try again'), findsOneWidget);
  });
}
