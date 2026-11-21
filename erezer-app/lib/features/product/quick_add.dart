import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../app/theme.dart';
import '../../data/api_client.dart';
import '../../data/cart.dart';
import '../../data/models.dart';
import '../../data/pricing.dart' as pricing;

/// What happened when "Add to Cart" was tapped on a product card.
enum QuickAddResult { added, unavailable, failed }

/// A variant that is something to choose: a size, or a colour (or other option).
bool _isChoice(Variant v) => v.size != null || v.choices.isNotEmpty;

/// What a variant is called on a cart line: "Black · Drop Shoulder · M".
String variantLabel(Product p, Variant? v) {
  if (v == null) return '';
  return [
    for (final o in p.options) ?o.values.where((x) => x.id == v.choices[o.id]).firstOrNull?.value,
    if (v.fit != null) _fitName(v),
    ?v.size,
  ].join(' · ');
}

String _fitName(Variant v) =>
    v.fitLabel ?? v.fit!.split('_').map((w) => w.isEmpty ? w : w[0] + w.substring(1).toLowerCase()).join(' ');

/// The cart line for one of [p] in [v] (null: no size, or not chosen yet), at the
/// price the product page would show.
CartLine cartLineFor(
  Product p,
  Variant? v,
  pricing.PriceBook book, {
  int quantity = 1,
  bool sizeMissing = false,
}) {
  final own = v?.priceOverride;
  final base = own != null ? pricing.ownPriceAfterSale(p, own) : pricing.basePrice(p);
  return CartLine(
    productId: p.id,
    variantId: v?.id,
    quantity: quantity,
    name: p.name,
    label: variantLabel(p, v),
    imageUrl: p.imageUrl,
    unitPrice: book.priceOf(p, base),
    maxQuantity: v?.stockQuantity ?? p.stockQuantity,
    sizeMissing: sizeMissing,
  );
}

/// "Add to Cart" on a product card, as on the website: one goes into the cart
/// straight away. A product with a single size (or none) gets that size; one
/// with a choice goes in without a size, and the cart and checkout ask for it.
Future<QuickAddResult> addFromList(WidgetRef ref, int productId) async {
  final api = ref.read(apiProvider);
  try {
    final p = await api.product(productId);
    final variants = await api.variants(productId);
    if (!p.isAvailable || (p.stockQuantity != null && p.stockQuantity! <= 0)) return QuickAddResult.unavailable;
    final choices = variants.where(_isChoice).toList();
    final single = choices.length <= 1;
    final v = single ? variants.firstOrNull : null;
    if (v != null && !v.inStock) return QuickAddResult.unavailable;
    ref
        .read(cartProvider.notifier)
        .add(cartLineFor(p, v, ref.read(priceBookProvider), sizeMissing: !single));
    return QuickAddResult.added;
  } catch (_) {
    return QuickAddResult.failed;
  }
}

const _sizeOrder = ['XXS', 'XS', 'S', 'M', 'L', 'XL', 'XXL', '2XL', 'XXXL', '3XL', '4XL', '5XL'];

/// "Choose your size" on a cart line that has none yet (added from a card):
/// the fits (or colours) first when there are several, then the sizes; tapping
/// one gives the line that size. A line that has its size gets "Change size",
/// and, where asked, "Remove".
class CartSizePicker extends ConsumerStatefulWidget {
  const CartSizePicker({super.key, required this.line, this.removable = false});
  final CartLine line;

  /// Also offer "Remove" (where the page has no remove button of its own).
  final bool removable;

  @override
  ConsumerState<CartSizePicker> createState() => _CartSizePickerState();
}

class _CartSizePickerState extends ConsumerState<CartSizePicker> {
  bool _changing = false;

  /// The fit (or colour) picked; null until the shopper picks one.
  String? _group;

  static String _groupOf(Variant v) {
    final keys = v.choices.keys.toList()..sort();
    final options = keys.map((k) => '$k=${v.choices[k]}').join(',');
    return options.isEmpty && v.fit == null ? '' : '$options|${v.fit ?? ''}';
  }

  static String _groupLabel(Product p, Variant v) => [
    for (final o in p.options) ?o.values.where((x) => x.id == v.choices[o.id]).firstOrNull?.value,
    if (v.fit != null) _fitName(v),
  ].join(' · ');

  void _choose(ProductDetails d, Variant v) {
    final line = widget.line;
    setState(() => _changing = false);
    if (v.id == line.variantId) return;
    ref
        .read(cartProvider.notifier)
        .replace(line, cartLineFor(d.product, v, ref.read(priceBookProvider), quantity: line.quantity));
  }

  @override
  Widget build(BuildContext context) {
    final line = widget.line;
    final details = ref.watch(productDetailsProvider(line.productId)).value;
    final variants = details?.variants.where(_isChoice).toList() ?? const <Variant>[];
    final linkStyle = TextStyle(
      fontSize: 13,
      fontWeight: FontWeight.w500,
      decoration: TextDecoration.underline,
      color: AppColors.muted,
    );

    if (!line.sizeMissing && !_changing) {
      final canChange = line.variantId != null && variants.length > 1;
      if (!canChange && !widget.removable) return const SizedBox.shrink();
      return Padding(
        padding: const EdgeInsets.only(top: 4),
        child: Wrap(
          spacing: 14,
          children: [
            if (canChange)
              InkWell(
                key: Key('size-change-${line.key}'),
                onTap: () {
                  // Opens on the fit of the size in use.
                  final current = variants.where((v) => v.id == line.variantId).firstOrNull;
                  setState(() {
                    _group = current == null || _groupOf(current).isEmpty ? null : _groupOf(current);
                    _changing = true;
                  });
                },
                child: Text('Change size', style: linkStyle),
              ),
            if (widget.removable)
              InkWell(
                onTap: () => ref.read(cartProvider.notifier).remove(line.key),
                child: Text('Remove', style: linkStyle.copyWith(color: const Color(0xFFDC2626))),
              ),
          ],
        ),
      );
    }

    final amberText = AppColors.dark ? const Color(0xFFFDE68A) : const Color(0xFF78350F);
    Widget box(List<Widget> children) => Container(
      key: Key('size-picker-${line.key}'),
      margin: const EdgeInsets.only(top: 8),
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: AppColors.dark ? const Color(0x4078350F) : const Color(0xFFFFFBEB),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.dark ? const Color(0xFFB45309) : const Color(0xFFFCD34D)),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: children),
    );

    if (details == null) {
      return box([
        const SizedBox(height: 18, width: 18, child: CircularProgressIndicator(strokeWidth: 2)),
      ]);
    }

    final p = details.product;
    final groups = <String, String>{};
    for (final v in variants) {
      final key = _groupOf(v);
      if (key.isNotEmpty) groups.putIfAbsent(key, () => _groupLabel(p, v));
    }
    // The fit shown: the one picked, else the first with something in stock.
    final current =
        _group ??
        groups.keys.where((g) => variants.any((v) => _groupOf(v) == g && v.inStock)).firstOrNull ??
        groups.keys.firstOrNull;
    int rank(String? s) {
      final i = _sizeOrder.indexOf((s ?? '').toUpperCase());
      return i < 0 ? _sizeOrder.length : i;
    }

    final sizes = variants.where((v) => groups.length > 1 ? _groupOf(v) == current : true).toList()
      ..sort((a, b) => rank(a.size) - rank(b.size));

    Widget chip(String label, bool on, VoidCallback? onTap, {Key? key, bool round = false}) => InkWell(
      key: key,
      onTap: onTap,
      borderRadius: BorderRadius.circular(round ? 20 : 6),
      child: Container(
        constraints: const BoxConstraints(minWidth: 36, minHeight: 32),
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          color: on ? AppColors.ink : AppColors.card,
          borderRadius: BorderRadius.circular(round ? 20 : 6),
          border: Border.all(color: on ? AppColors.ink : AppColors.line, width: on && !round ? 2 : 1),
        ),
        child: Text(
          label,
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w600,
            color: on ? AppColors.onInk : (onTap == null ? AppColors.muted : AppColors.ink),
            decoration: onTap == null ? TextDecoration.lineThrough : null,
          ),
        ),
      ),
    );

    return box([
      Row(
        children: [
          Expanded(
            child: Text(
              '${_changing ? 'CHANGE' : 'CHOOSE'} YOUR ${groups.length > 1 ? 'FIT AND SIZE' : 'SIZE'}',
              style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, letterSpacing: 0.5, color: amberText),
            ),
          ),
          if (_changing)
            InkWell(
              onTap: () => setState(() => _changing = false),
              child: Text(
                'Cancel',
                style: TextStyle(fontSize: 12, color: amberText, decoration: TextDecoration.underline),
              ),
            ),
        ],
      ),
      if (groups.length > 1) ...[
        const SizedBox(height: 6),
        Wrap(
          spacing: 4,
          runSpacing: 4,
          children: [
            for (final g in groups.entries)
              chip(g.value, current == g.key, () => setState(() => _group = g.key), round: true),
          ],
        ),
      ],
      const SizedBox(height: 6),
      Wrap(
        spacing: 4,
        runSpacing: 4,
        children: [
          for (final v in sizes)
            chip(
              v.size ?? _groupLabel(p, v),
              v.id == line.variantId,
              v.inStock ? () => _choose(details, v) : null,
              key: Key('size-option-${line.productId}-${v.id}'),
            ),
        ],
      ),
    ]);
  }
}
