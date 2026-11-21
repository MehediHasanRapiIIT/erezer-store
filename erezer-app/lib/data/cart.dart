import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// One item in the bag: a product in a chosen size (variant) and how many.
class CartLine {
  const CartLine({
    required this.productId,
    required this.quantity,
    required this.name,
    required this.unitPrice,
    this.variantId,
    this.label = '',
    this.imageUrl,
    this.maxQuantity,
  });

  factory CartLine.fromJson(Map<String, dynamic> j) => CartLine(
    productId: j['productId'] as int,
    variantId: j['variantId'] as int?,
    quantity: j['quantity'] as int? ?? 1,
    name: j['name'] as String? ?? '',
    label: j['label'] as String? ?? '',
    imageUrl: j['imageUrl'] as String?,
    unitPrice: (j['unitPrice'] as num?)?.toDouble() ?? 0,
    maxQuantity: j['maxQuantity'] as int?,
  );

  Map<String, dynamic> toJson() => {
    'productId': productId,
    'variantId': variantId,
    'quantity': quantity,
    'name': name,
    'label': label,
    'imageUrl': imageUrl,
    'unitPrice': unitPrice,
    'maxQuantity': maxQuantity,
  };

  final int productId;
  final int? variantId;
  final int quantity;
  final String name;

  /// What was picked, e.g. "Drop Shoulder · M".
  final String label;
  final String? imageUrl;

  /// The price shown when it was added. The checkout asks the server for the real total.
  final double unitPrice;

  /// How many the shop has, when it counts them.
  final int? maxQuantity;

  String get key => '$productId:${variantId ?? ''}';

  CartLine withQuantity(int q) => CartLine(
    productId: productId,
    variantId: variantId,
    quantity: q,
    name: name,
    label: label,
    imageUrl: imageUrl,
    unitPrice: unitPrice,
    maxQuantity: maxQuantity,
  );
}

/// At most this many of one item in one order.
const maxPerLine = 10;

/// The bag, kept on the phone so it survives closing the app.
class CartNotifier extends Notifier<List<CartLine>> {
  static const _storageKey = 'erezer.cart.v1';

  @override
  List<CartLine> build() {
    _load();
    return const [];
  }

  Future<void> _load() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final raw = prefs.getString(_storageKey);
      if (raw == null) return;
      final saved = (jsonDecode(raw) as List).whereType<Map<String, dynamic>>().map(CartLine.fromJson).toList();
      // Anything added while the saved bag was loading stays too.
      final added = {for (final l in state) l.key: l};
      state = [
        for (final l in saved)
          if (!added.containsKey(l.key)) l,
        ...state,
      ];
    } catch (_) {
      // A damaged saved bag starts empty rather than stopping the app.
    }
  }

  Future<void> _save() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_storageKey, jsonEncode(state.map((l) => l.toJson()).toList()));
    } catch (_) {}
  }

  int _limit(CartLine l) {
    final stock = l.maxQuantity;
    return stock != null && stock > 0 && stock < maxPerLine ? stock : maxPerLine;
  }

  /// Adds the item, or more of it when it is already in the bag. Returns false
  /// when the bag already holds as many as can be ordered.
  bool add(CartLine line) {
    final i = state.indexWhere((l) => l.key == line.key);
    if (i < 0) {
      state = [...state, line.withQuantity(line.quantity.clamp(1, _limit(line)))];
    } else {
      final current = state[i];
      final limit = _limit(line);
      if (current.quantity >= limit) return false;
      state = [...state]..[i] = line.withQuantity((current.quantity + line.quantity).clamp(1, limit));
    }
    _save();
    return true;
  }

  void setQuantity(String key, int quantity) {
    if (quantity <= 0) return remove(key);
    state = [for (final l in state) l.key == key ? l.withQuantity(quantity.clamp(1, _limit(l))) : l];
    _save();
  }

  bool canAddOne(CartLine l) => l.quantity < _limit(l);

  void remove(String key) {
    state = state.where((l) => l.key != key).toList();
    _save();
  }

  void clear() {
    state = const [];
    _save();
  }
}

final cartProvider = NotifierProvider<CartNotifier, List<CartLine>>(CartNotifier.new);

/// How many items are in the bag, for the badge on the Cart tab.
final cartCountProvider = Provider<int>((ref) => ref.watch(cartProvider).fold(0, (n, l) => n + l.quantity));

/// The customer's delivery details from their last order, so they needn't
/// type them again. Kept only on this phone.
class SavedDetails {
  const SavedDetails({
    this.firstName = '',
    this.lastName = '',
    this.email = '',
    this.phone = '',
    this.address = '',
    this.zoneId,
  });

  factory SavedDetails.fromJson(Map<String, dynamic> j) => SavedDetails(
    firstName: j['firstName'] as String? ?? '',
    lastName: j['lastName'] as String? ?? '',
    email: j['email'] as String? ?? '',
    phone: j['phone'] as String? ?? '',
    address: j['address'] as String? ?? '',
    zoneId: j['zoneId'] as int?,
  );

  Map<String, dynamic> toJson() => {
    'firstName': firstName,
    'lastName': lastName,
    'email': email,
    'phone': phone,
    'address': address,
    'zoneId': zoneId,
  };

  final String firstName;
  final String lastName;
  final String email;
  final String phone;
  final String address;
  final int? zoneId;

  static const _storageKey = 'erezer.details.v1';

  static Future<SavedDetails> load() async {
    try {
      final raw = (await SharedPreferences.getInstance()).getString(_storageKey);
      return raw == null ? const SavedDetails() : SavedDetails.fromJson(jsonDecode(raw) as Map<String, dynamic>);
    } catch (_) {
      return const SavedDetails();
    }
  }

  Future<void> save() async {
    try {
      await (await SharedPreferences.getInstance()).setString(_storageKey, jsonEncode(toJson()));
    } catch (_) {}
  }
}
