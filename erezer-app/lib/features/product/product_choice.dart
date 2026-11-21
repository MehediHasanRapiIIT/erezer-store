import '../../data/models.dart';
import '../../data/pricing.dart' as pricing;

/// What the customer has picked on a product page (colour and other options,
/// fit, size) and what follows from it. Mirrors the website's product page:
/// it opens on the first combination in stock, Drop Shoulder (or the only fit)
/// and the first size in stock.
class ProductChoice {
  const ProductChoice._(this.product, this.variants, this.choices, this.fit, this.size);

  factory ProductChoice.start(Product product, List<Variant> variants) {
    final opening =
        variants.where((v) => v.choices.isNotEmpty && v.inStock).firstOrNull ??
        variants.where((v) => v.choices.isNotEmpty).firstOrNull;
    final withChoices = ProductChoice._(product, variants, {...?opening?.choices}, null, null);
    final withFit = withChoices._copy(fit: withChoices.fits.firstOrNull);
    return withFit._copy(size: withFit.sizes.where(withFit.isSizeAvailable).firstOrNull);
  }

  final Product product;
  final List<Variant> variants;

  /// Option id → chosen value id.
  final Map<String, String> choices;
  final String? fit;
  final String? size;

  ProductChoice _copy({Map<String, String>? choices, Object? fit = _keep, Object? size = _keep}) => ProductChoice._(
    product,
    variants,
    choices ?? this.choices,
    identical(fit, _keep) ? this.fit : fit as String?,
    identical(size, _keep) ? this.size : size as String?,
  );
  static const _keep = Object();

  bool _matches(Variant v, Map<String, String> chosen) => product.options.every((o) => v.choices[o.id] == chosen[o.id]);

  /// The variants of the chosen colour (and other options): all, when there are none.
  List<Variant> get optionVariants =>
      product.options.isEmpty ? variants : variants.where((v) => _matches(v, choices)).toList();

  /// The fits on offer, in the shop's order.
  List<String> get fits => {
    for (final v in optionVariants)
      if (v.fit != null) v.fit!,
  }.toList();

  List<Variant> get fitVariants => fits.isEmpty ? optionVariants : optionVariants.where((v) => v.fit == fit).toList();

  static const _sizeOrder = ['XS', 'S', 'M', 'L', 'XL', 'XXL', 'XXXL'];

  /// The sizes on offer for the picked fit, smallest first.
  List<String> get sizes {
    final all = {
      for (final v in fitVariants)
        if (v.size != null) v.size!,
    }.toList();
    all.sort((a, b) {
      final ia = _sizeOrder.indexOf(a), ib = _sizeOrder.indexOf(b);
      if (ia >= 0 && ib >= 0) return ia - ib;
      if (ia >= 0) return -1;
      if (ib >= 0) return 1;
      return a.compareTo(b);
    });
    return all;
  }

  bool isSizeAvailable(String s) => fitVariants.any((v) => v.size == s && v.inStock);

  /// True when this choice, with the others as they are, has something in stock.
  bool hasStockWith(String optionId, String valueId) {
    final chosen = {...choices, optionId: valueId};
    return variants.any((v) => v.inStock && _matches(v, chosen));
  }

  Variant? get variant {
    if (variants.isEmpty) return null;
    return fitVariants.where((v) => v.size == size).firstOrNull;
  }

  ProductChoice withChoice(String optionId, String valueId) {
    if (choices[optionId] == valueId) return this;
    var next = _copy(choices: {...choices, optionId: valueId});
    if (fit == null || !next.fits.contains(fit)) next = next._copy(fit: next.fits.firstOrNull);
    if (size == null || !next.isSizeAvailable(size!)) {
      next = next._copy(size: next.sizes.where(next.isSizeAvailable).firstOrNull);
    }
    return next;
  }

  ProductChoice withFit(String f) {
    if (fit == f) return this;
    final next = _copy(fit: f);
    if (size != null && next.isSizeAvailable(size!)) return next;
    return next._copy(size: next.sizes.where(next.isSizeAvailable).firstOrNull);
  }

  ProductChoice withSize(String s) => _copy(size: s);

  String fitLabel(String f) {
    final label = variants.where((v) => v.fit == f && v.fitLabel != null).firstOrNull?.fitLabel;
    if (label != null) return label;
    return f.split('_').map((w) => w.isEmpty ? w : w[0] + w.substring(1).toLowerCase()).join(' ');
  }

  String valueName(ProductOption o) => o.values.where((v) => v.id == choices[o.id]).firstOrNull?.value ?? '';

  /// "Black · Drop Shoulder · L", for messages.
  String get summary => [
    for (final o in product.options)
      if (valueName(o).isNotEmpty) valueName(o),
    if (fit != null && fits.length > 1) fitLabel(fit!),
    ?size,
  ].join(' · ');

  /// The price before automatic discounts: the size's or fit's own price with
  /// the product's sale off it, else the product's sale-or-regular price.
  double get basePrice {
    final own = variant?.priceOverride;
    return own != null ? pricing.ownPriceAfterSale(product, own) : pricing.basePrice(product);
  }

  /// The price before any sale, to strike through.
  double get regularPrice => variant?.priceOverride ?? product.price;

  /// IN_STOCK, LOW_STOCK or OUT_OF_STOCK for what is picked.
  String get stockStatus {
    final v = variant;
    if (v != null) {
      final qty = v.stockQuantity ?? 0;
      if (qty <= 0) return 'OUT_OF_STOCK';
      if (qty <= 3) return 'LOW_STOCK';
      return 'IN_STOCK';
    }
    if (!product.isAvailable) return 'OUT_OF_STOCK';
    return switch (product.stockStatus) {
      'LOW_STOCK' => 'LOW_STOCK',
      'OUT_OF_STOCK' => 'OUT_OF_STOCK',
      _ => 'IN_STOCK',
    };
  }

  /// How many are left of the picked size, or of every size together.
  int? get stockCount {
    if (variant != null) return variant!.stockQuantity;
    final counted = variants.map((v) => v.stockQuantity).whereType<int>();
    if (counted.isNotEmpty) return counted.fold<int>(0, (sum, q) => sum + (q > 0 ? q : 0));
    return product.stockQuantity;
  }

  /// The stock line, with the real number when the shop shows quantities.
  String stockText(String status) {
    if (status == 'OUT_OF_STOCK') return 'Out of stock';
    final count = stockCount;
    if (product.showStockQuantity && count != null && count > 0) {
      return status == 'LOW_STOCK' ? 'Only $count left' : '$count in stock';
    }
    return status == 'IN_STOCK' ? 'In stock' : 'Only a few left';
  }

  bool get canAddToCart {
    if (variants.isNotEmpty && variant == null) return false;
    return stockStatus != 'OUT_OF_STOCK';
  }

  String get addToCartLabel {
    if (variants.isNotEmpty && variant == null) {
      return sizes.isNotEmpty && size == null ? 'Select a size' : 'Select options';
    }
    if (stockStatus == 'OUT_OF_STOCK') return 'Out of stock';
    return 'Add to cart';
  }

  /// The photos for the chosen colour first, then those that suit every colour.
  List<ProductImage> photos(List<ProductImage> all) {
    final chosen = choices.values.toSet();
    return [
      ...all.where((i) => i.optionValueId != null && chosen.contains(i.optionValueId)),
      ...all.where((i) => i.optionValueId == null),
    ];
  }

  /// The size chart for the picked fit (or the product), from the shop's chart
  /// library; the charts in the shop settings stand in for a shop without one.
  SizeChart? sizeChart(Map<int, SizeChart> library, StoreSettings? settings) {
    final id = _chartIdFor(fit);
    final fromLibrary = id == null ? null : library[id];
    if (fromLibrary != null) return fromLibrary;
    return (fit == null ? null : settings?.fitSizeCharts[fit]) ?? settings?.sizeChart;
  }

  int? _chartIdFor(String? f) {
    if (f == null) return product.effectiveSizeChartId;
    return product.effectiveFitSizeChartIds[f] ??
        (f == 'REGULAR_FIT' ? product.effectiveRegularFitSizeChartId : null) ??
        product.effectiveSizeChartId;
  }
}
