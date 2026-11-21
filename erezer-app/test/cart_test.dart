import 'package:erezer_app/data/cart.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

CartLine _line({int product = 1, int? variant = 10, int qty = 1, int? stock}) => CartLine(
  productId: product,
  variantId: variant,
  quantity: qty,
  name: 'Tee',
  unitPrice: 500,
  label: 'M',
  maxQuantity: stock,
);

Future<ProviderContainer> _container() async {
  final c = ProviderContainer();
  addTearDown(c.dispose);
  c.read(cartProvider);
  // Let the saved bag finish loading.
  await Future<void>.delayed(Duration.zero);
  return c;
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test('the same item twice adds to its quantity; another size is its own line', () async {
    final c = await _container();
    final cart = c.read(cartProvider.notifier);
    cart.add(_line());
    cart.add(_line());
    cart.add(_line(variant: 11));
    expect(c.read(cartProvider).map((l) => l.quantity), [2, 1]);
    expect(c.read(cartCountProvider), 3);
  });

  test('never more than the shop has, nor more than ten', () async {
    final c = await _container();
    final cart = c.read(cartProvider.notifier);
    expect(cart.add(_line(stock: 2)), isTrue);
    expect(cart.add(_line(stock: 2)), isTrue);
    expect(cart.add(_line(stock: 2)), isFalse);
    expect(c.read(cartProvider).single.quantity, 2);

    cart.add(_line(product: 2, qty: 30));
    expect(c.read(cartProvider).last.quantity, maxPerLine);
  });

  test('quantity zero removes the item', () async {
    final c = await _container();
    final cart = c.read(cartProvider.notifier);
    cart.add(_line());
    cart.setQuantity('1:10', 0);
    expect(c.read(cartProvider), isEmpty);
  });

  test('the bag is still there after the app is closed and opened again', () async {
    final first = await _container();
    first.read(cartProvider.notifier).add(_line(qty: 3));
    await Future<void>.delayed(Duration.zero);

    final reopened = await _container();
    final lines = reopened.read(cartProvider);
    expect(lines.single.quantity, 3);
    expect(lines.single.label, 'M');
  });
}
