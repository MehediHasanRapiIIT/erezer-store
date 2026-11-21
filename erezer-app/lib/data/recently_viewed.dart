import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'models.dart';

/// The products the customer opened last, newest first, kept on the phone for
/// the home page's "Recently viewed" band (like the website's).
class RecentlyViewedNotifier extends Notifier<List<Product>> {
  static const _storageKey = 'erezer.recent.v1';
  static const _max = 12;

  @override
  List<Product> build() {
    _load();
    return const [];
  }

  Future<void> _load() async {
    try {
      final raw = (await SharedPreferences.getInstance()).getString(_storageKey);
      if (raw == null) return;
      final saved = (jsonDecode(raw) as List).whereType<Map<String, dynamic>>().map(Product.fromJson).toList();
      final seen = {for (final p in state) p.id};
      state = [...state, ...saved.where((p) => !seen.contains(p.id))].take(_max).toList();
    } catch (_) {}
  }

  void viewed(Product p) {
    state = [p, ...state.where((x) => x.id != p.id)].take(_max).toList();
    SharedPreferences.getInstance().then(
      (prefs) => prefs.setString(_storageKey, jsonEncode(state.map((p) => p.toCardJson()).toList())),
    );
  }
}

final recentlyViewedProvider = NotifierProvider<RecentlyViewedNotifier, List<Product>>(RecentlyViewedNotifier.new);
