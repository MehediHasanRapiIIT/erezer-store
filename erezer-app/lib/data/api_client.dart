import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;

import '../app/config.dart';
import 'cart.dart';
import 'models.dart';
import 'pricing.dart';

/// A problem talking to the server, worded for the customer.
class ApiException implements Exception {
  ApiException(this.message, {this.statusCode, this.code});
  final String message;

  /// The server's answer code (401 = not logged in), when it answered.
  final int? statusCode;

  /// The server's own name for the problem, e.g. EMAIL_NOT_VERIFIED.
  final String? code;
  @override
  String toString() => message;
}

/// How the shop list is ordered, as the website offers it.
enum ProductSort {
  featured('featured', 'Featured'),
  priceLow('price-asc', 'Price: low to high'),
  priceHigh('price-desc', 'Price: high to low');

  const ProductSort(this.param, this.label);
  final String param;
  final String label;
}

/// Talks to the same Erezer server the website uses.
class ApiClient {
  ApiClient({http.Client? client}) : _http = client ?? http.Client();
  final http.Client _http;

  Map<String, String> _headers(String? token, {bool json = false}) => {
    'Accept': 'application/json',
    if (json) 'Content-Type': 'application/json',
    if (token != null) 'Authorization': 'Bearer $token',
  };

  Future<dynamic> _get(String path, [Map<String, String>? query, String? token]) =>
      _send(() => _http.get(_uri(path, query), headers: _headers(token)));

  Future<dynamic> _post(String path, Object body, {String? token}) =>
      _send(() => _http.post(_uri(path, null), headers: _headers(token, json: true), body: jsonEncode(body)));

  Future<dynamic> _put(String path, Object body, {required String token}) =>
      _send(() => _http.put(_uri(path, null), headers: _headers(token, json: true), body: jsonEncode(body)));

  Uri _uri(String path, Map<String, String>? query) => Uri.parse('$apiBaseUrl$path').replace(queryParameters: query);

  Future<dynamic> _send(Future<http.Response> Function() call) async {
    final http.Response res;
    try {
      res = await call().timeout(const Duration(seconds: 20));
    } catch (_) {
      throw ApiException("Can't reach Erezer. Please check your internet and try again.");
    }
    final body = res.bodyBytes.isEmpty ? null : _decode(res);
    if (res.statusCode >= 200 && res.statusCode < 300) return body;
    // The server words most problems for the customer in "message".
    final message = body is Map ? body['message'] : null;
    final code = body is Map ? body['code'] as String? : null;
    if (message is String && message.trim().isNotEmpty && res.statusCode < 500) {
      throw ApiException(message, statusCode: res.statusCode, code: code);
    }
    throw ApiException(
      switch (res.statusCode) {
        401 => 'Please log in again.',
        404 => 'This item is no longer available.',
        429 => 'Too many tries. Please wait a minute and try again.',
        _ => 'Something went wrong (${res.statusCode}). Please try again.',
      },
      statusCode: res.statusCode,
      code: code,
    );
  }

  dynamic _decode(http.Response res) {
    try {
      return jsonDecode(utf8.decode(res.bodyBytes));
    } catch (_) {
      return null;
    }
  }

  List<Map<String, dynamic>> _maps(Object? v) => v is List ? v.whereType<Map<String, dynamic>>().toList() : const [];

  Future<List<Category>> categories() async =>
      _maps(await _get('/api/categories')).map(Category.fromJson).where((c) => c.isActive).toList();

  Future<StoreSettings> storeSettings() async =>
      StoreSettings.fromJson(await _get('/api/store-settings') as Map<String, dynamic>);

  Future<FlashSale?> flashSale() async {
    final j = await _get('/api/flash-sale');
    return j is Map<String, dynamic> ? FlashSale.fromJson(j) : null;
  }

  Future<HomeData> home() async => HomeData.fromJson(await _get('/app/home') as Map<String, dynamic>);

  Future<List<ActiveDiscount>> activeDiscounts() async =>
      _maps(await _get('/api/discounts/active')).map(ActiveDiscount.fromJson).toList();

  /// One page of the shop list: a category (with its sub-categories), a search, or everything.
  Future<PageOf<Product>> browse({
    int? categoryId,
    String? query,
    ProductSort sort = ProductSort.featured,
    int page = 0,
    int size = 20,
  }) async {
    final j = await _get('/api/products/browse', {
      if (categoryId != null) 'categoryId': '$categoryId',
      if (query != null && query.trim().isNotEmpty) 'q': query.trim(),
      'sort': sort.param,
      'page': '$page',
      'size': '$size',
    }) as Map<String, dynamic>;
    return PageOf(
      items: _maps(j['content']).map(Product.fromJson).toList(),
      total: j['totalElements'] as int? ?? 0,
      isLast: j['last'] as bool? ?? true,
    );
  }

  Future<Product> product(int id) async => Product.fromJson(await _get('/api/products/$id') as Map<String, dynamic>);

  Future<List<Variant>> variants(int id) async =>
      _maps(await _get('/api/products/$id/variants')).map(Variant.fromJson).toList();

  Future<List<ProductImage>> images(int id) async =>
      _maps(await _get('/api/products/$id/images')).map(ProductImage.fromJson).toList()
        ..sort((a, b) => a.sortOrder.compareTo(b.sortOrder));

  Future<List<Product>> related(int id) async =>
      _maps(await _get('/api/products/$id/related', {'limit': '8'})).map(Product.fromJson).toList();

  Future<Map<int, SizeChart>> sizeCharts() async => {
    for (final e in _maps(await _get('/api/size-charts'))) e['id'] as int: ?SizeChart.maybe(e['chart']),
  };

  Future<List<ShippingZone>> shippingZones() async =>
      _maps(await _get('/api/shipping/zones')).map(ShippingZone.fromJson).toList();

  /// What the bag costs with this zone and code, worked out by the server.
  Future<CheckoutQuote> quote(List<CartLine> lines, {int? zoneId, String? address, String? couponCode}) async =>
      CheckoutQuote.fromJson(
        await _post('/api/checkout/quote', {
          'items': _items(lines),
          'shippingZoneId': ?zoneId,
          if (address != null && address.trim().isNotEmpty) 'deliveryAddress': address.trim(),
          if (couponCode != null && couponCode.trim().isNotEmpty) 'couponCode': couponCode.trim(),
        }) as Map<String, dynamic>,
      );

  /// Places an order without an account; the shop contacts the customer on the details given.
  Future<PlacedOrder> placeGuestOrder({
    required List<CartLine> lines,
    required String firstName,
    required String lastName,
    required String email,
    required String phone,
    required String address,
    required String paymentMethod,
    int? zoneId,
    String? couponCode,
    String? pushToken,
  }) async => PlacedOrder.fromJson(
    await _post('/app/consumer/guest/orders', {
      // The phone that placed it gets the order's updates.
      'pushToken': ?pushToken,
      'firstName': firstName.trim(),
      'lastName': lastName.trim(),
      'email': email.trim(),
      'phone': phone.trim(),
      'deliveryAddress': address.trim(),
      'paymentMethod': paymentMethod,
      'shopId': 1,
      'items': _items(lines),
      'shippingZoneId': ?zoneId,
      if (couponCode != null && couponCode.trim().isNotEmpty) 'couponCode': couponCode.trim(),
    }) as Map<String, dynamic>,
  );

  /// Starts a bKash payment for a bKash order; answers bKash's page to open.
  /// Starts a bKash payment for a bKash order, by its id or (from Track an order) its number.
  Future<BkashPayment> bkashInit({String? orderId, String? orderNumber}) async => BkashPayment.fromJson(
    await _post('/api/payments/bkash/init', orderId != null ? {'orderId': orderId} : {'orderNumber': orderNumber})
        as Map<String, dynamic>,
  );

  /// How the payment went, confirming it with bKash if that hasn't happened yet. Safe to repeat.
  Future<BkashPayment> bkashExecute(String paymentId) async => BkashPayment.fromJson(
    await _post('/api/payments/bkash/execute', {'paymentId': paymentId}) as Map<String, dynamic>,
  );

  /// Passes bKash's "back to the shop" address on to the shop's server, which
  /// records the payment (paid, failed or cancelled) and answers with where it
  /// would send a website customer. Returns that address's status: success,
  /// failure or cancel.
  Future<String> bkashReturn(Uri fromBkash) async {
    final uri = Uri.parse('$apiBaseUrl/api/payments/bkash/callback')
        .replace(queryParameters: fromBkash.queryParameters);
    try {
      final request = http.Request('GET', uri)..followRedirects = false;
      final res = await http.Response.fromStream(await _http.send(request).timeout(const Duration(seconds: 30)));
      final next = Uri.tryParse(res.headers['location'] ?? '');
      return next?.queryParameters['status'] ?? fromBkash.queryParameters['status'] ?? 'failure';
    } catch (_) {
      // The payment is checked again with bkashExecute, so carry on with what bKash said.
      return fromBkash.queryParameters['status'] ?? 'failure';
    }
  }

  // ─── Accounts (the same as the website's) ───────────────────────────────────

  Future<AuthTokens> login(String email, String password) async => AuthTokens.fromJson(
    await _post('/app/auth/login', {'email': email.trim(), 'password': password}) as Map<String, dynamic>,
  );

  Future<AuthTokens> register({
    required String firstName,
    required String lastName,
    required String email,
    required String password,
  }) async => AuthTokens.fromJson(
    await _post('/app/auth/register', {
      'firstName': firstName.trim(),
      'lastName': lastName.trim(),
      'email': email.trim(),
      'password': password,
    }) as Map<String, dynamic>,
  );

  Future<AuthTokens> refresh(String refreshToken) async =>
      AuthTokens.fromJson(await _post('/app/auth/refresh', {'refreshToken': refreshToken}) as Map<String, dynamic>);

  Future<String> resendVerification(String email) async =>
      _message(await _post('/app/auth/resend-verification', {'email': email.trim()}), 'We sent you a new email.');

  Future<String> forgotPassword(String email) async => _message(
    await _post('/app/auth/forgot-password', {'email': email.trim()}),
    'If that email has an account, we sent a link to set a new password.',
  );

  String _message(Object? body, String fallback) =>
      body is Map && body['message'] is String ? body['message'] as String : fallback;

  /// A logged-in customer's order, kept in their order history.
  Future<PlacedOrder> placeOrder({
    required String userId,
    required String token,
    required List<CartLine> lines,
    required String phone,
    required String address,
    required String paymentMethod,
    int? zoneId,
    String? couponCode,
    String? pushToken,
  }) async => PlacedOrder.fromJson(
    await _post('/app/consumer/$userId/orders', {
      'pushToken': ?pushToken,
      'deliveryAddress': address.trim(),
      'phone': phone.trim(),
      'paymentMethod': paymentMethod,
      'shopId': 1,
      'deliveryCharge': 0,
      'latitude': null,
      'longitude': null,
      'items': _items(lines),
      'shippingZoneId': ?zoneId,
      if (couponCode != null && couponCode.trim().isNotEmpty) 'couponCode': couponCode.trim(),
    }, token: token) as Map<String, dynamic>,
  );

  Future<PageOf<Order>> orders(String userId, String token, {int page = 0, int size = 10}) async {
    final j = await _get('/app/consumer/$userId/orders/paged', {
      'page': '$page',
      'size': '$size',
    }, token) as Map<String, dynamic>;
    return PageOf(
      items: _maps(j['content']).map(Order.fromJson).toList(),
      total: j['totalElements'] as int? ?? 0,
      isLast: j['last'] as bool? ?? true,
    );
  }

  Future<Order> order(String userId, String token, String orderId) async =>
      Order.fromJson(await _get('/app/consumer/$userId/orders/$orderId', null, token) as Map<String, dynamic>);

  Future<OrderProgress> orderProgress(String userId, String token, String orderId) async => OrderProgress.fromJson(
    await _get('/app/consumer/$userId/orders/$orderId/track', null, token) as Map<String, dynamic>,
  );

  Future<Order> cancelOrder(String userId, String token, String orderId, String reason) async => Order.fromJson(
    await _post('/app/consumer/$userId/orders/$orderId/cancel', {
      if (reason.trim().isNotEmpty) 'reason': reason.trim(),
    }, token: token) as Map<String, dynamic>,
  );

  Future<Profile> profile(String userId, String token) async =>
      Profile.fromJson(await _get('/app/consumer/$userId/profile', null, token) as Map<String, dynamic>);

  Future<Profile> updateProfile(String userId, String token, Profile p) async => Profile.fromJson(
    await _put('/app/consumer/$userId/profile', {
      'firstName': p.firstName.trim(),
      'lastName': p.lastName.trim(),
      'email': p.email,
      'latitude': null,
      'longitude': null,
    }, token: token) as Map<String, dynamic>,
  );

  // ─── Order notifications (Firebase) ─────────────────────────────────────────

  /// This phone runs the app; the shop can send it order updates.
  Future<void> registerDevice(String token) => _post('/api/push/devices', {'token': token});

  /// The logged-in customer's orders' updates come to this phone too.
  Future<void> linkDevice(String userId, String accessToken, String token) =>
      _post('/app/consumer/$userId/push-devices', {'token': token}, token: accessToken);

  /// Logged out here: that customer's updates stop coming to this phone.
  Future<void> unlinkDevice(String userId, String accessToken, String token) =>
      _post('/app/consumer/$userId/push-devices/remove', {'token': token}, token: accessToken);

  /// Deletes the customer's own account; their password confirms it.
  Future<String> deleteAccount(String userId, String token, String password) async => _message(
    await _post('/app/consumer/$userId/account/delete', {'password': password}, token: token),
    'Your account has been deleted.',
  );

  /// Any order's progress by its number (EZ-482915); nothing about the person.
  Future<PublicTracking> trackByNumber(String number) async =>
      PublicTracking.fromJson(await _get('/api/orders/track', {'number': number.trim()}) as Map<String, dynamic>);

  List<Map<String, dynamic>> _items(List<CartLine> lines) => [
    for (final l in lines) {'productId': l.productId, 'variantId': l.variantId, 'quantity': l.quantity},
  ];

  Future<PageOf<Review>> reviews(int productId, {int page = 0, int size = 5}) async {
    final j =
        await _get('/api/products/$productId/reviews', {'page': '$page', 'size': '$size'}) as Map<String, dynamic>;
    return PageOf(
      items: _maps(j['content']).map(Review.fromJson).toList(),
      total: j['totalElements'] as int? ?? 0,
      isLast: j['last'] as bool? ?? true,
    );
  }
}

final apiProvider = Provider<ApiClient>((ref) => ApiClient());

final categoriesProvider = FutureProvider<List<Category>>((ref) => ref.watch(apiProvider).categories());

final storeSettingsProvider = FutureProvider<StoreSettings>((ref) => ref.watch(apiProvider).storeSettings());

final homeProvider = FutureProvider<HomeData>((ref) => ref.watch(apiProvider).home());

/// The flash sale running now, or null.
final flashSaleProvider = FutureProvider<FlashSale?>((ref) async {
  final sale = await ref.watch(apiProvider).flashSale();
  return sale == null || sale.endsAt.isBefore(DateTime.now()) || sale.products.isEmpty ? null : sale;
});

/// The automatic discounts and the category tree, for showing prices. While
/// either is loading (or failed), prices show without automatic discounts.
final priceBookProvider = Provider<PriceBook>((ref) {
  final discounts = ref.watch(_discountsProvider).value ?? const [];
  final categories = ref.watch(categoriesProvider).value ?? const [];
  return PriceBook(discounts: discounts, parents: {for (final c in categories) c.id: c.parentId});
});

final _discountsProvider = FutureProvider<List<ActiveDiscount>>((ref) => ref.watch(apiProvider).activeDiscounts());

final sizeChartsProvider = FutureProvider<Map<int, SizeChart>>((ref) => ref.watch(apiProvider).sizeCharts());

/// Everything the product page needs, loaded together.
class ProductDetails {
  ProductDetails({required this.product, required this.variants, required this.images});
  final Product product;
  final List<Variant> variants;
  final List<ProductImage> images;
}

final productDetailsProvider = FutureProvider.autoDispose.family<ProductDetails, int>((ref, id) async {
  final api = ref.watch(apiProvider);
  // Future.wait passes the first problem on as it is, so the page can word it.
  final all = await Future.wait<Object>([api.product(id), api.variants(id), api.images(id)]);
  return ProductDetails(
    product: all[0] as Product,
    variants: all[1] as List<Variant>,
    images: all[2] as List<ProductImage>,
  );
});

final relatedProvider = FutureProvider.autoDispose.family<List<Product>, int>(
  (ref, id) => ref.watch(apiProvider).related(id),
);

final reviewsProvider = FutureProvider.autoDispose.family<PageOf<Review>, int>(
  (ref, id) => ref.watch(apiProvider).reviews(id),
);

final shippingZonesProvider = FutureProvider<List<ShippingZone>>((ref) => ref.watch(apiProvider).shippingZones());
