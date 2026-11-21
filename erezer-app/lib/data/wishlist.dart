import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'models.dart';

/// Products the customer hearted, newest first, kept on the phone (as the
/// website keeps them in the browser).
class WishlistNotifier extends Notifier<List<Product>> {
  static const _storageKey = 'erezer.wishlist.v1';

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
      final here = {for (final p in state) p.id};
      state = [...state, ...saved.where((p) => !here.contains(p.id))];
    } catch (_) {}
  }

  bool has(int productId) => state.any((p) => p.id == productId);

  /// Hearts or un-hearts it; true when it is now in the wishlist.
  bool toggle(Product p) {
    final adding = !has(p.id);
    state = adding ? [p, ...state] : state.where((x) => x.id != p.id).toList();
    SharedPreferences.getInstance().then(
      (prefs) => prefs.setString(_storageKey, jsonEncode(state.map((x) => x.toCardJson()).toList())),
    );
    return adding;
  }
}

final wishlistProvider = NotifierProvider<WishlistNotifier, List<Product>>(WishlistNotifier.new);
