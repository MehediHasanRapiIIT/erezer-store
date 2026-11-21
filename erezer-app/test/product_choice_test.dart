import 'package:erezer_app/data/models.dart';
import 'package:erezer_app/features/product/product_choice.dart';
import 'package:flutter_test/flutter_test.dart';

final _colour = ProductOption(
  id: 'c',
  name: 'Colour',
  isColour: true,
  values: [
    OptionValue(id: 'black', value: 'Black', hex: '#000000'),
    OptionValue(id: 'white', value: 'White', hex: '#ffffff'),
  ],
);

Product _product({List<ProductOption> options = const [], bool showStock = false}) => Product(
  id: 1,
  categoryId: 1,
  name: 'Tee',
  description: '',
  price: 1000,
  discountPrice: 900,
  isAvailable: true,
  options: options,
  showStockQuantity: showStock,
  effectiveSizeChartId: 3,
  effectiveFitSizeChartIds: const {'DROP_SHOULDER': 1},
);

Variant _v(int id, String size, int? stock, {String? fit, String? colour, double? own}) => Variant(
  id: id,
  size: size,
  fit: fit,
  fitLabel: fit == 'DROP_SHOULDER' ? 'Drop Shoulder' : (fit == null ? null : 'Regular Fit'),
  stockQuantity: stock,
  priceOverride: own,
  choices: colour == null ? const {} : {'c': colour},
);

void main() {
  test('opens on the first size in stock and offers sizes smallest first', () {
    final c = ProductChoice.start(_product(), [_v(1, 'XL', 4), _v(2, 'S', 0), _v(3, 'M', 2)]);
    expect(c.sizes, ['S', 'M', 'XL']);
    expect(c.size, 'M');
    expect(c.isSizeAvailable('S'), isFalse);
    expect(c.canAddToCart, isTrue);
    expect(c.stockStatus, 'LOW_STOCK');
    expect(c.stockText(c.stockStatus), 'Only a few left');
  });

  test('shows the real count when the shop turned it on', () {
    final c = ProductChoice.start(_product(showStock: true), [_v(1, 'M', 12)]);
    expect(c.stockText(c.stockStatus), '12 in stock');
  });

  test('nothing in stock: the button says so', () {
    final c = ProductChoice.start(_product(), [_v(1, 'M', 0), _v(2, 'L', 0)]);
    expect(c.size, isNull);
    expect(c.canAddToCart, isFalse);
    expect(c.addToCartLabel, 'Select a size');
  });

  test('a fit has its own sizes, price and size chart', () {
    final c = ProductChoice.start(_product(), [
      _v(1, 'M', 5, fit: 'DROP_SHOULDER', own: 1200),
      _v(2, 'L', 5, fit: 'DROP_SHOULDER', own: 1200),
      _v(3, 'L', 5, fit: 'REGULAR_FIT'),
      _v(4, 'XL', 5, fit: 'REGULAR_FIT'),
    ]);
    expect(c.fits, ['DROP_SHOULDER', 'REGULAR_FIT']);
    expect(c.fitLabel('DROP_SHOULDER'), 'Drop Shoulder');
    expect(c.size, 'M');
    // ৳1,200 with the product's 10% sale off it.
    expect(c.basePrice, 1080);
    expect(c.regularPrice, 1200);
    final chart = SizeChart(
      columns: ['Chest'],
      rows: [
        SizeChartRow(size: 'M', cells: [(cm: 100, inch: 40)]),
      ],
    );
    expect(c.sizeChart({1: chart}, null), same(chart));

    // Regular Fit has no M: the first size it has in stock is picked.
    final regular = c.withFit('REGULAR_FIT');
    expect(regular.size, 'L');
    expect(regular.basePrice, 900);
    expect(regular.withSize('XL').variant?.id, 4);
  });

  test('a colour shows its own sizes and photos', () {
    final c = ProductChoice.start(_product(options: [_colour]), [
      _v(1, 'M', 0, colour: 'black'),
      _v(2, 'M', 3, colour: 'white'),
      _v(3, 'L', 3, colour: 'white'),
    ]);
    // Black has nothing in stock, so the page opens on White.
    expect(c.choices['c'], 'white');
    expect(c.hasStockWith('c', 'black'), isFalse);
    expect(c.summary, 'White · M');

    final photos = [
      ProductImage(id: 1, url: 'b.jpg', sortOrder: 0, optionValueId: 'black'),
      ProductImage(id: 2, url: 'w.jpg', sortOrder: 1, optionValueId: 'white'),
      ProductImage(id: 3, url: 'all.jpg', sortOrder: 2),
    ];
    expect(c.photos(photos).map((p) => p.url), ['w.jpg', 'all.jpg']);

    final black = c.withChoice('c', 'black');
    expect(black.size, isNull);
    expect(black.canAddToCart, isFalse);
    expect(black.photos(photos).map((p) => p.url), ['b.jpg', 'all.jpg']);
  });

  test('a product without sizes can be added straight away', () {
    final c = ProductChoice.start(_product(), const []);
    expect(c.canAddToCart, isTrue);
    expect(c.addToCartLabel, 'Add to cart');
  });
}
