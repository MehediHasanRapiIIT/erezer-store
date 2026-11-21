import 'package:erezer_app/data/models.dart';
import 'package:erezer_app/data/pricing.dart';
import 'package:flutter_test/flutter_test.dart';

Product _product({double price = 1000, double? sale, bool byAmount = false, int category = 5, bool excluded = false}) =>
    Product(
      id: 1,
      categoryId: category,
      name: 'Tee',
      description: '',
      price: price,
      discountPrice: sale,
      saleByAmount: byAmount,
      isAvailable: true,
      discountExcluded: excluded,
    );

ActiveDiscount _discount(
  String scope,
  double value, {
  bool percent = true,
  int? target,
  int priority = 0,
  bool stackable = false,
  String id = 'a',
}) => ActiveDiscount(
  id: id,
  scope: scope,
  isPercent: percent,
  value: value,
  priority: priority,
  stackable: stackable,
  targetId: target,
);

void main() {
  test('the sale price is used when there is one', () {
    expect(basePrice(_product(sale: 849)), 849);
    expect(basePrice(_product(sale: 0)), 1000);
    expect(basePrice(_product()), 1000);
  });

  test("a size's own price gets the product's sale too", () {
    // 10% off as a percentage sale.
    expect(ownPriceAfterSale(_product(price: 1000, sale: 900), 1200), 1080);
    // ৳100 off as an amount sale.
    expect(ownPriceAfterSale(_product(price: 1000, sale: 900, byAmount: true), 1200), 1100);
    // No sale: left as it is.
    expect(ownPriceAfterSale(_product(), 1200), 1200);
  });

  test('a category discount reaches its sub-categories', () {
    final book = PriceBook(discounts: [_discount('CATEGORY', 10, target: 20)], parents: {5: 20, 20: null});
    expect(book.cardPrice(_product(category: 5)), 900);
    expect(book.cardPrice(_product(category: 7)), 1000);
  });

  test('the highest priority discount wins unless they stack', () {
    final single = [_discount('GLOBAL', 10, priority: 1, id: 'a'), _discount('GLOBAL', 20, priority: 5, id: 'b')];
    expect(lineDiscount(1, 5, 1000, single), 200);

    final stacking = [
      _discount('GLOBAL', 10, priority: 5, stackable: true, id: 'a'),
      _discount('GLOBAL', 100, percent: false, priority: 1, stackable: true, id: 'b'),
    ];
    // 10% of 1000, then ৳100 off what is left.
    expect(lineDiscount(1, 5, 1000, stacking), 200);
  });

  test('an excluded product keeps its own price', () {
    final book = PriceBook(discounts: [_discount('GLOBAL', 50)]);
    expect(book.cardPrice(_product(sale: 800, excluded: true)), 800);
    expect(book.cardPrice(_product(sale: 800)), 400);
  });

  test('taka shows thousands and drops .00', () {
    expect(taka(1099), '৳1,099');
    expect(taka(849.5), '৳849.50');
    expect(taka(1250000), '৳1,250,000');
  });
}
