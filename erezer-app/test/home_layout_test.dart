import 'dart:convert';

import 'package:erezer_app/data/api_client.dart';
import 'package:erezer_app/main.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

Map<String, dynamic> _banner(String slot, String title) => {
  'slot': slot,
  'imageUrl': '',
  'promotionTitle': title,
  'ctaLabel': 'SHOP NOW',
  'ctaLink': '/x',
};

/// A shop whose admin switched the tile grid off and put categories first.
MockClient _shop(List<Map<String, dynamic>>? layout) => MockClient((req) async {
  final body = switch (req.url.path) {
    '/api/store-settings' => {'homeLayout': ?layout},
    '/app/home' => {
      'banners': [_banner('GRID_1', 'Grid One'), _banner('CUSTOM_PROMO', 'Make it yours')],
      'newArrivalItems': [
        {'id': 1, 'categoryId': 1, 'name': 'New Tee', 'description': '', 'price': 500, 'isAvailable': true},
      ],
      'featuredItems': [],
      'popularItems': [
        {'id': 2, 'categoryId': 1, 'name': 'Popular Tee', 'description': '', 'price': 500, 'isAvailable': true},
      ],
      'homeSections': [],
    },
    '/api/categories' => [
      {'id': 1, 'name': 'Tees', 'isActive': true, 'productCount': 1, 'shopOrder': 1, 'slug': 'tees'},
    ],
    _ => [],
  };
  return http.Response(jsonEncode(body), 200, headers: {'content-type': 'application/json; charset=utf-8'});
});

Future<void> _start(WidgetTester tester, List<Map<String, dynamic>>? layout) async {
  tester.view.physicalSize = const Size(1080, 4000);
  tester.view.devicePixelRatio = 2.6;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      retry: noRetry,
      overrides: [apiProvider.overrideWithValue(ApiClient(client: _shop(layout)))],
      child: const ErezerApp(),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  setUpAll(() => GoogleFonts.config.allowRuntimeFetching = false);
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});
  });

  testWidgets('a band the admin switched off is not shown, and the order is theirs', (tester) async {
    await _start(tester, [
      {'key': 'SHOP_BY_CATEGORY', 'enabled': true},
      {'key': 'NEW_ARRIVALS', 'enabled': true},
      {'key': 'TILE_GRID', 'enabled': false},
      {'key': 'CUSTOM_DESIGN_PROMO', 'enabled': true},
    ]);
    final page = find.descendant(of: find.byKey(const Key('home-list')), matching: find.byType(Scrollable)).first;
    // Shop by category above New arrivals, as the admin put them: further down the page.
    double offsetOf(Finder f) => tester.state<ScrollableState>(page).position.pixels + tester.getTopLeft(f).dy;
    await tester.scrollUntilVisible(find.text('Shop by category'), 300, scrollable: page);
    final categories = offsetOf(find.text('Shop by category'));
    await tester.scrollUntilVisible(find.text('New arrivals'), 300, scrollable: page);
    expect(offsetOf(find.text('New arrivals')), greaterThan(categories));
    await tester.scrollUntilVisible(find.text('MAKE IT YOURS'), 300, scrollable: page);
    expect(find.byKey(const Key('banner-GRID_1')), findsNothing);
    // The website's home page has no "popular" band, so neither does the app.
    expect(find.text('Popular Tee'), findsNothing);
  });

  testWidgets('a shop without a layout shows every band the app has', (tester) async {
    await _start(tester, null);
    final page = find.descendant(of: find.byKey(const Key('home-list')), matching: find.byType(Scrollable)).first;
    await tester.scrollUntilVisible(find.byKey(const Key('banner-GRID_1')), 300, scrollable: page);
    await tester.scrollUntilVisible(find.text('New arrivals'), 300, scrollable: page);
  });
}
