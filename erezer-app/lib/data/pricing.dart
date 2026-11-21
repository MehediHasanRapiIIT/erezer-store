import 'dart:math' as math;

import 'models.dart';

/// The price rules of the website (core/discount-pricing.ts), so the app shows
/// the same prices. The server's checkout quote is still what the customer pays.

/// The product's own sale price when it has one, otherwise its regular price.
double basePrice(Product p) => p.discountPrice != null && p.discountPrice! > 0 ? p.discountPrice! : p.price;

/// A size's or fit's own price with the product's sale taken off it too:
/// "10% off" takes 10% off it, "৳100 off" takes 100 off it.
double ownPriceAfterSale(Product p, double own) {
  final sale = p.discountPrice;
  if (!(p.price > 0) || sale == null || !(sale > 0) || sale >= p.price) return own;
  final after = p.saleByAmount ? own - (p.price - sale) : (own * sale / p.price * 100).round() / 100;
  return after > 0 ? after : own;
}

/// The shop's automatic discounts as they apply in [categoryId]: a discount on
/// a parent category counts for its sub-categories too.
List<ActiveDiscount> discountsFor(int categoryId, List<ActiveDiscount> all, Map<int, int?> parents) {
  final above = <int>{};
  for (var up = parents[categoryId]; up != null && up != categoryId && !above.contains(up); up = parents[up]) {
    above.add(up);
  }
  if (above.isEmpty) return all;
  return [
    for (final d in all) d.scope == 'CATEGORY' && above.contains(d.targetId) ? d.copyWith(targetId: categoryId) : d,
  ];
}

/// How much the automatic discounts take off one item.
double lineDiscount(int productId, int categoryId, double subtotal, List<ActiveDiscount> discounts) {
  if (subtotal <= 0 || discounts.isEmpty) return 0;
  final candidates = discounts
      .where(
        (d) => switch (d.scope) {
          'GLOBAL' => true,
          'CATEGORY' => d.targetId == categoryId,
          'PRODUCT' => d.targetId == productId,
          _ => false,
        },
      )
      .toList();
  if (candidates.isEmpty) return 0;
  candidates.sort((a, b) {
    if (a.priority != b.priority) return b.priority - a.priority;
    if (a.value != b.value) return b.value.compareTo(a.value);
    return a.id.compareTo(b.id);
  });
  final anchor = candidates.first;
  final applied = [anchor, if (anchor.stackable) ...candidates.skip(1).where((d) => d.stackable)];
  var remaining = subtotal;
  for (final d in applied) {
    final cut = d.isPercent ? math.min(remaining * d.value / 100, remaining) : math.min(d.value, remaining);
    remaining -= cut;
    if (remaining <= 0) {
      remaining = 0;
      break;
    }
  }
  return (subtotal - remaining).clamp(0, subtotal).toDouble();
}

/// What the shop's prices look like right now, for working out a product's price.
class PriceBook {
  PriceBook({this.discounts = const [], this.parents = const {}});
  final List<ActiveDiscount> discounts;

  /// Category id → its parent's id.
  final Map<int, int?> parents;

  /// The price to pay for one, from [base] (the sale-or-regular price).
  double priceOf(Product p, double base) {
    if (p.discountExcluded || p.categoryDiscountExcluded) return base;
    return base - lineDiscount(p.id, p.categoryId, base, discountsFor(p.categoryId, discounts, parents));
  }

  /// The card price of a product.
  double cardPrice(Product p) => priceOf(p, basePrice(p));
}

/// ৳1,099 or ৳849.50.
String taka(double v) {
  final whole = v == v.roundToDouble();
  final s = whole ? v.round().toString() : v.toStringAsFixed(2);
  final parts = s.split('.');
  final digits = parts[0].replaceAllMapped(RegExp(r'\B(?=(\d{3})+(?!\d))'), (_) => ',');
  return '৳$digits${parts.length > 1 ? '.${parts[1]}' : ''}';
}
