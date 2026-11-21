import 'dart:convert';

import 'package:erezer_app/data/api_client.dart';
import 'package:erezer_app/data/auth.dart';
import 'package:erezer_app/main.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

Map<String, dynamic> _tokens({bool verified = true, String access = 'A1'}) => {
  'userId': 'u1',
  'email': 'rahim@example.com',
  'firstName': 'Rahim',
  'lastName': 'Uddin',
  'emailVerified': verified,
  'accessToken': access,
  'refreshToken': 'R1',
  'expiresInSeconds': 300,
};

Map<String, dynamic> _order(String status, {String method = 'BKASH', String? state = 'PENDING'}) => {
  'id': 'o1',
  'orderNumber': 'EZ-111111',
  'orderStatus': status,
  'totalAmount': 1870,
  'deliveryCharge': 70,
  'paymentMethod': method,
  'paymentState': state,
  'createdAt': '2026-11-20T10:00:00',
  'deliveryAddress': 'House 1, Dhanmondi',
  'orderItems': [
    {'productName': 'Anime Hoodie', 'quantity': 1, 'priceAtOrder': 1800, 'variantSize': 'M'},
  ],
};

/// What the app sent, by "METHOD path".
final _sent = <String, http.Request>{};
var _verified = true;
var _status = 'PLACED';

/// The access token the server accepts; another one gets 401 (expired).
var _goodToken = 'A1';

final _server = MockClient((req) async {
  _sent['${req.method} ${req.url.path}'] = req;
  http.Response json(Object body, [int code = 200]) =>
      http.Response(jsonEncode(body), code, headers: {'content-type': 'application/json; charset=utf-8'});
  final authed = req.headers['Authorization'] == 'Bearer $_goodToken';
  if (req.url.path.startsWith('/app/consumer/u1') && !authed) return json({'message': 'Unauthorized'}, 401);
  return switch ('${req.method} ${req.url.path}') {
    'POST /app/auth/login' =>
      (jsonDecode(req.body) as Map)['password'] == 'wrongpass'
          ? json({'message': 'Wrong email or password.'}, 401)
          : json(_tokens(verified: _verified)),
    'POST /app/auth/refresh' => json(_tokens(verified: _verified, access: _goodToken)),
    'GET /app/consumer/u1/orders/paged' => json({
      'content': [_order(_status)],
      'totalElements': 1,
      'last': true,
    }),
    'GET /app/consumer/u1/orders/o1' => json(_order(_status)),
    'GET /app/consumer/u1/orders/o1/track' => json({
      'currentStatus': _status,
      'history': [
        {'toStatus': 'PLACED', 'createdAt': '2026-11-20T10:00:00'},
      ],
      'allowedCustomerNextStates': _status == 'PLACED' ? ['CANCELLED'] : [],
    }),
    'POST /app/consumer/u1/orders/o1/cancel' => () {
      _status = 'CANCELLED';
      return json(_order('CANCELLED'));
    }(),
    'GET /api/orders/track' =>
      req.url.queryParameters['number'] == 'EZ-222222'
          ? json({
              'orderNumber': 'EZ-222222',
              'status': 'SHIPPED',
              'placedAt': '2026-11-19T09:00:00',
              'steps': [
                {'status': 'SHIPPED', 'at': '2026-11-20T12:00:00'},
              ],
              'items': [
                {'name': 'Classic Tee', 'quantity': 2, 'unitPrice': 500, 'lineTotal': 1000, 'size': 'L'},
              ],
              'total': 1060,
              'shipping': 60,
              'paymentMethod': 'CASH',
            })
          : json({'message': 'Order not found.'}, 404),
    'POST /app/consumer/u1/account/delete' =>
      (jsonDecode(req.body) as Map)['password'] == 'secret123'
          ? json({'message': 'Your account has been deleted.'})
          : json({'message': 'That password is not right.'}, 400),
    'GET /api/categories' || 'GET /api/discounts/active' || 'GET /api/size-charts' => json([]),
    'GET /api/store-settings' => json({}),
    'GET /app/home' => json({'banners': [], 'newArrivalItems': [], 'popularItems': [], 'featuredItems': []}),
    _ => json({'message': 'not found'}, 404),
  };
});

Future<ProviderContainer> _start(WidgetTester tester) async {
  tester.view.physicalSize = const Size(1080, 2400);
  tester.view.devicePixelRatio = 2.6;
  addTearDown(tester.view.reset);
  final container = ProviderContainer(
    retry: noRetry,
    overrides: [apiProvider.overrideWithValue(ApiClient(client: _server))],
  );
  addTearDown(container.dispose);
  await tester.pumpWidget(UncontrolledProviderScope(container: container, child: const ErezerApp()));
  await tester.pumpAndSettle();
  return container;
}

Future<void> _type(WidgetTester tester, String key, String text) =>
    tester.enterText(find.descendant(of: find.byKey(Key(key)), matching: find.byType(EditableText)), text);

Future<void> _login(WidgetTester tester, {String password = 'secret123'}) async {
  await tester.tap(find.text('Account'));
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const Key('go-login')));
  await tester.pumpAndSettle();
  await _type(tester, 'login-email', 'rahim@example.com');
  await _type(tester, 'login-password', password);
  await tester.tap(find.byKey(const Key('login-submit')));
  await tester.pumpAndSettle();
}

void main() {
  setUpAll(() => GoogleFonts.config.allowRuntimeFetching = false);
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});
    _sent.clear();
    _verified = true;
    _status = 'PLACED';
    _goodToken = 'A1';
  });

  testWidgets('a wrong password says so; the right one logs in and stays logged in', (tester) async {
    final container = await _start(tester);
    await _login(tester, password: 'wrongpass');
    expect(find.text('Wrong email or password.'), findsOneWidget);

    await _type(tester, 'login-password', 'secret123');
    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pumpAndSettle();
    expect(find.text('Hello, Rahim!'), findsOneWidget);
    expect(find.byKey(const Key('verify-email')), findsNothing);
    expect(container.read(authProvider)?.userId, 'u1');

    // Kept in secure storage: a fresh start finds the login.
    final again = ProviderContainer(overrides: [apiProvider.overrideWithValue(ApiClient(client: _server))]);
    addTearDown(again.dispose);
    again.read(authProvider);
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 50)));
    expect(again.read(authProvider)?.email, 'rahim@example.com');

    await tester.tap(find.byKey(const Key('logout')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('go-login')), findsOneWidget);
  });

  testWidgets('an unconfirmed email is asked for, and checked again on request', (tester) async {
    _verified = false;
    await _start(tester);
    await _login(tester);
    expect(find.byKey(const Key('verify-email')), findsOneWidget);
    _verified = true;
    await tester.tap(find.byKey(const Key('check-verified')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('verify-email')), findsNothing);
  });

  testWidgets('my orders, order details with Pay now, and cancelling', (tester) async {
    await _start(tester);
    await _login(tester);
    await tester.tap(find.byKey(const Key('my-orders')));
    await tester.pumpAndSettle();
    expect(find.text('EZ-111111'), findsOneWidget);
    expect(find.text('bKash · Not paid yet'), findsOneWidget);

    await tester.tap(find.text('EZ-111111'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('order-timeline')), findsOneWidget);
    expect(find.byKey(const Key('pay-now')), findsOneWidget);
    expect(find.text('Pay ৳1,870 with bKash'), findsOneWidget);
    expect(find.text('Anime Hoodie'), findsOneWidget);

    await tester.scrollUntilVisible(
      find.byKey(const Key('cancel-order')),
      200,
      scrollable: find.byType(Scrollable).last,
    );
    await tester.tap(find.byKey(const Key('cancel-order')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('confirm-cancel')));
    await tester.pumpAndSettle();
    expect(_sent.containsKey('POST /app/consumer/u1/orders/o1/cancel'), isTrue);
    expect(find.text('This order was cancelled.'), findsOneWidget);
    expect(find.byKey(const Key('cancel-order')), findsNothing);
    expect(find.byKey(const Key('pay-now')), findsNothing);
  });

  testWidgets('an expired login is renewed without the customer noticing', (tester) async {
    final container = await _start(tester);
    await _login(tester);
    // The server now wants a newer token than the one the app holds.
    _goodToken = 'A2';
    final orders = await tester.runAsync(
      () => container.read(authProvider.notifier).call((api, s) => api.orders(s.userId, s.accessToken)),
    );
    expect(orders!.items.single.orderNumber, 'EZ-111111');
    expect(_sent.containsKey('POST /app/auth/refresh'), isTrue);
    expect(container.read(authProvider)?.accessToken, 'A2');
  });

  testWidgets('track any order by its number, without logging in', (tester) async {
    await _start(tester);
    await tester.tap(find.text('Account'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('track-order')));
    await tester.pumpAndSettle();

    await tester.enterText(find.byKey(const Key('track-number')), 'EZ-999999');
    await tester.tap(find.byKey(const Key('track-go')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('track-error')), findsOneWidget);

    await tester.enterText(find.byKey(const Key('track-number')), 'EZ-222222');
    await tester.tap(find.byKey(const Key('track-go')));
    await tester.pumpAndSettle();
    expect(find.text('Shipped'), findsWidgets);
    expect(find.text('Classic Tee'), findsOneWidget);
    expect(find.text('Cash on delivery'), findsOneWidget);
    expect(find.byKey(const Key('pay-now')), findsNothing);
  });

  testWidgets('delete my account: a wrong password is refused, the right one deletes and logs out', (tester) async {
    final container = await _start(tester);
    await _login(tester);
    await tester.scrollUntilVisible(
      find.byKey(const Key('delete-account')),
      200,
      scrollable: find.byType(Scrollable).last,
    );
    await tester.tap(find.byKey(const Key('delete-account')));
    await tester.pumpAndSettle();

    await tester.enterText(find.byKey(const Key('delete-password')), 'nope');
    await tester.tap(find.byKey(const Key('delete-confirm')));
    await tester.pumpAndSettle();
    expect(find.text('That password is not right.'), findsOneWidget);
    expect(container.read(authProvider), isNotNull);

    await tester.enterText(find.byKey(const Key('delete-password')), 'secret123');
    await tester.tap(find.byKey(const Key('delete-confirm')));
    await tester.pumpAndSettle();
    expect(container.read(authProvider), isNull);
    expect(find.text('Your account has been deleted.'), findsOneWidget);
    expect(find.byKey(const Key('go-login')), findsOneWidget);
  });

  testWidgets('the thank-you page can open Track this order', (tester) async {
    await _start(tester);
    GoRouter.of(tester.element(find.byType(Scaffold).first)).go('/account/track?number=EZ-222222');
    await tester.pumpAndSettle();
    expect(find.text('EZ-222222'), findsWidgets);
    expect(find.text('Classic Tee'), findsOneWidget);
  });
}
