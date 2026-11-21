/// The shop's data as the Erezer server sends it. Only the fields the app uses
/// are read; anything else in the JSON is ignored.
library;

double _num(Object? v) => v is num ? v.toDouble() : 0;
double? _numOrNull(Object? v) => v is num ? v.toDouble() : null;
List<Map<String, dynamic>> _list(Object? v) => v is List ? v.whereType<Map<String, dynamic>>().toList() : const [];

/// A shop category, as /api/categories sends it. Categories form a tree:
/// [parentId] is null for the top level.
class Category {
  Category({
    required this.id,
    required this.name,
    required this.slug,
    required this.isActive,
    required this.productCount,
    required this.shopOrder,
    this.imageUrl,
    this.parentId,
  });

  factory Category.fromJson(Map<String, dynamic> j) => Category(
    id: j['id'] as int,
    name: j['name'] as String? ?? '',
    slug: j['slug'] as String? ?? '',
    isActive: j['isActive'] as bool? ?? true,
    productCount: j['productCount'] as int? ?? 0,
    shopOrder: j['shopOrder'] as int? ?? 0,
    imageUrl: j['imageUrl'] as String?,
    parentId: j['parentId'] as int?,
  );

  final int id;
  final String name;
  final String slug;
  final bool isActive;
  final int productCount;
  final int shopOrder;
  final String? imageUrl;
  final int? parentId;
}

/// The shop details the app needs from /api/store-settings.
class StoreSettings {
  StoreSettings({
    this.supportPhone,
    this.supportHours,
    this.returnPolicyText,
    this.sizeChart,
    this.fitSizeCharts = const {},
    this.payCod = true,
    this.payBkash = true,
    this.payCard = true,
    this.couponsEnabled = true,
    this.homeLayout,
    this.highlights = const [],
    this.story,
    this.marquee = const [],
    this.promises = const [],
    this.outlets = const [],
  });

  factory StoreSettings.fromJson(Map<String, dynamic> j) => StoreSettings(
    supportPhone: j['supportPhone'] as String?,
    supportHours: j['supportHours'] as String?,
    returnPolicyText: j['returnPolicyText'] as String?,
    sizeChart: SizeChart.maybe(j['sizeChart']),
    fitSizeCharts: {
      for (final e in ((j['fitSizeCharts'] as Map<String, dynamic>?) ?? {}).entries) e.key: ?SizeChart.maybe(e.value),
    },
    payCod: j['paymentCodEnabled'] as bool? ?? true,
    payBkash: j['paymentBkashEnabled'] as bool? ?? true,
    payCard: j['paymentCardEnabled'] as bool? ?? true,
    couponsEnabled: j['couponsEnabled'] as bool? ?? true,
    homeLayout: j['homeLayout'] is List
        ? [
            for (final s in _list(j['homeLayout']))
              if (s['enabled'] == true && s['key'] is String) s['key'] as String,
          ]
        : null,
    highlights: _list(j['highlights']).map(Highlight.fromJson).toList(),
    story: j['brandStory'] is Map<String, dynamic>
        ? BrandStory.fromJson(j['brandStory'] as Map<String, dynamic>)
        : null,
    marquee: (j['marquee'] is Map && (j['marquee'] as Map)['enabled'] != false)
        ? (((j['marquee'] as Map)['items'] as List?) ?? const []).whereType<String>().toList()
        : const [],
    promises: _list((j['footer'] as Map<String, dynamic>?)?['promises']).map(FooterPromise.fromJson).toList(),
    outlets: _list((j['footer'] as Map<String, dynamic>?)?['outlets']).map(ShopOutlet.fromJson).toList(),
  );

  final String? supportPhone;
  final String? supportHours;
  final String? returnPolicyText;

  /// The shop's general chart, for a shop without a chart library.
  final SizeChart? sizeChart;
  final Map<String, SizeChart> fitSizeCharts;
  final bool payCod;
  final bool payBkash;
  final bool payCard;

  /// The admin's promo code switch.
  final bool couponsEnabled;

  /// The home page bands the admin left switched on, top to bottom (Admin →
  /// Home page layout); null from a shop that never set one.
  final List<String>? homeLayout;

  /// The admin's highlights band ("2–4 days · Fast delivery").
  final List<Highlight> highlights;

  /// "Our story" on the home page.
  final BrandStory? story;

  /// The scrolling strip's words; empty when switched off.
  final List<String> marquee;

  /// The shop's promises and shops (the website's footer).
  final List<FooterPromise> promises;
  final List<ShopOutlet> outlets;

  /// The ways to pay the shop takes: CASH, BKASH, CARD.
  List<String> get paymentMethods => [if (payCod) 'CASH', if (payBkash) 'BKASH', if (payCard) 'CARD'];
}

/// One option of a product ("Colour") and its choices.
class ProductOption {
  ProductOption({required this.id, required this.name, required this.isColour, required this.values});

  factory ProductOption.fromJson(Map<String, dynamic> j) => ProductOption(
    id: j['id'] as String,
    name: j['name'] as String? ?? '',
    isColour: j['kind'] == 'COLOUR',
    values: _list(j['values'])
        .map((v) => OptionValue(id: v['id'] as String, value: v['value'] as String? ?? '', hex: v['hex'] as String?))
        .toList(),
  );

  final String id;
  final String name;
  final bool isColour;
  final List<OptionValue> values;
}

class OptionValue {
  OptionValue({required this.id, required this.value, this.hex});
  final String id;
  final String value;
  final String? hex;
}

class Product {
  Product({
    required this.id,
    required this.categoryId,
    required this.name,
    required this.description,
    required this.price,
    required this.isAvailable,
    this.discountPrice,
    this.saleByAmount = false,
    this.categoryName,
    this.productCode,
    this.imageUrl,
    this.isNewArrival = false,
    this.stockStatus,
    this.stockQuantity,
    this.showStockQuantity = false,
    this.discountExcluded = false,
    this.categoryDiscountExcluded = false,
    this.avgRating = 0,
    this.totalReviews = 0,
    this.material,
    this.careInstructions,
    this.options = const [],
    this.blockedPaymentMethods = const [],
    this.effectiveSizeChartId,
    this.effectiveRegularFitSizeChartId,
    this.effectiveFitSizeChartIds = const {},
  });

  factory Product.fromJson(Map<String, dynamic> j) => Product(
    id: j['id'] as int,
    categoryId: j['categoryId'] as int? ?? 0,
    name: (j['name'] as String? ?? '').trim(),
    description: j['description'] as String? ?? '',
    price: _num(j['price']),
    discountPrice: _numOrNull(j['discountPrice']),
    saleByAmount: j['saleByAmount'] as bool? ?? false,
    isAvailable: j['isAvailable'] as bool? ?? true,
    categoryName: j['categoryName'] as String?,
    productCode: j['productCode'] as String?,
    imageUrl: j['imageUrl'] as String?,
    isNewArrival: j['isNewArrival'] as bool? ?? false,
    stockStatus: j['stockStatus'] as String?,
    stockQuantity: j['stockQuantity'] as int?,
    showStockQuantity: j['showStockQuantity'] as bool? ?? false,
    discountExcluded: j['discountExcluded'] as bool? ?? false,
    categoryDiscountExcluded: j['categoryDiscountExcluded'] as bool? ?? false,
    avgRating: _num(j['avgRating']),
    totalReviews: j['totalReviews'] as int? ?? 0,
    material: j['material'] as String?,
    careInstructions: j['careInstructions'] as String?,
    options: _list(j['options']).map(ProductOption.fromJson).toList(),
    blockedPaymentMethods: ((j['blockedPaymentMethods'] as List?) ?? const []).whereType<String>().toList(),
    effectiveSizeChartId: j['effectiveSizeChartId'] as int?,
    effectiveRegularFitSizeChartId: j['effectiveRegularFitSizeChartId'] as int?,
    effectiveFitSizeChartIds: {
      for (final e in ((j['effectiveFitSizeChartIds'] as Map<String, dynamic>?) ?? {}).entries)
        if (e.value is int) e.key: e.value as int,
    },
  );

  final int id;
  final int categoryId;
  final String name;
  final String description;

  /// The regular price.
  final double price;

  /// The product's own sale price; null or 0 for none.
  final double? discountPrice;

  /// The sale was set as taka off rather than a percentage.
  final bool saleByAmount;
  final bool isAvailable;
  final String? categoryName;
  final String? productCode;
  final String? imageUrl;
  final bool isNewArrival;

  /// IN_STOCK, LOW_STOCK or OUT_OF_STOCK.
  final String? stockStatus;
  final int? stockQuantity;
  final bool showStockQuantity;
  final bool discountExcluded;
  final bool categoryDiscountExcluded;
  final double avgRating;
  final int totalReviews;
  final String? material;
  final String? careInstructions;
  final List<ProductOption> options;

  /// CASH, BKASH or CARD: ways this product can't be paid for.
  final List<String> blockedPaymentMethods;
  final int? effectiveSizeChartId;
  final int? effectiveRegularFitSizeChartId;
  final Map<String, int> effectiveFitSizeChartIds;

  bool get isOutOfStock => !isAvailable || stockStatus == 'OUT_OF_STOCK';

  /// What a product card needs, for keeping on the phone (wishlist, recently viewed).
  Map<String, dynamic> toCardJson() => {
    'id': id,
    'categoryId': categoryId,
    'categoryName': categoryName,
    'name': name,
    'description': '',
    'price': price,
    'discountPrice': discountPrice,
    'saleByAmount': saleByAmount,
    'imageUrl': imageUrl,
    'isAvailable': isAvailable,
    'isNewArrival': isNewArrival,
    'stockStatus': stockStatus,
    'discountExcluded': discountExcluded,
    'categoryDiscountExcluded': categoryDiscountExcluded,
  };
}

/// One buyable version of a product: a size, maybe in a fit and a colour.
class Variant {
  Variant({
    required this.id,
    this.size,
    this.fit,
    this.fitLabel,
    this.stockQuantity,
    this.priceOverride,
    this.choices = const {},
  });

  factory Variant.fromJson(Map<String, dynamic> j) => Variant(
    id: j['id'] as int,
    size: j['size'] as String?,
    fit: j['fit'] as String?,
    fitLabel: j['fitLabel'] as String?,
    stockQuantity: j['stockQuantity'] as int?,
    priceOverride: _numOrNull(j['priceOverride']),
    choices: {for (final o in _list(j['options'])) o['optionId'] as String: o['valueId'] as String},
  );

  final int id;
  final String? size;
  final String? fit;
  final String? fitLabel;

  /// Null when the shop doesn't count this one's stock.
  final int? stockQuantity;

  /// This size's or fit's own price; null for the product's price.
  final double? priceOverride;

  /// Option id → chosen value id, e.g. the colour this variant is.
  final Map<String, String> choices;

  bool get inStock => stockQuantity == null || stockQuantity! > 0;
}

class ProductImage {
  ProductImage({required this.id, required this.url, required this.sortOrder, this.optionValueId});

  factory ProductImage.fromJson(Map<String, dynamic> j) => ProductImage(
    id: j['id'] as int,
    url: j['url'] as String? ?? '',
    sortOrder: j['sortOrder'] as int? ?? 0,
    optionValueId: j['optionValueId'] as String?,
  );

  final int id;
  final String url;
  final int sortOrder;

  /// The colour (usually) this picture shows; null when it suits all.
  final String? optionValueId;
}

/// An automatic discount the shop is running.
class ActiveDiscount {
  ActiveDiscount({
    required this.id,
    required this.scope,
    required this.isPercent,
    required this.value,
    required this.priority,
    required this.stackable,
    this.targetId,
  });

  factory ActiveDiscount.fromJson(Map<String, dynamic> j) => ActiveDiscount(
    id: '${j['id']}',
    scope: j['scope'] as String? ?? '',
    isPercent: j['discountType'] == 'PERCENT',
    value: _num(j['discountValue']),
    priority: j['priority'] as int? ?? 0,
    stackable: j['stackable'] as bool? ?? false,
    targetId: j['targetId'] as int?,
  );

  ActiveDiscount copyWith({int? targetId}) => ActiveDiscount(
    id: id,
    scope: scope,
    isPercent: isPercent,
    value: value,
    priority: priority,
    stackable: stackable,
    targetId: targetId ?? this.targetId,
  );

  final String id;

  /// GLOBAL, CATEGORY or PRODUCT.
  final String scope;
  final bool isPercent;
  final double value;
  final int priority;
  final bool stackable;
  final int? targetId;
}

class ShopBanner {
  ShopBanner({
    required this.slot,
    required this.imageUrl,
    this.mobileImageUrl,
    this.title,
    this.details,
    this.ctaLabel,
    this.ctaLink,
  });

  factory ShopBanner.fromJson(Map<String, dynamic> j) => ShopBanner(
    slot: j['slot'] as String? ?? 'HERO',
    imageUrl: j['imageUrl'] as String? ?? '',
    mobileImageUrl: j['mobileImageUrl'] as String?,
    title: j['promotionTitle'] as String?,
    details: j['promotionDetails'] as String?,
    ctaLabel: j['ctaLabel'] as String?,
    ctaLink: j['ctaLink'] as String?,
  );

  final String slot;
  final String imageUrl;
  final String? mobileImageUrl;
  final String? title;
  final String? details;
  final String? ctaLabel;
  final String? ctaLink;
}

class HomeSection {
  HomeSection({required this.categoryId, required this.name, required this.products});
  final int categoryId;
  final String name;
  final List<Product> products;
}

/// Everything the home page shows, from /app/home in one go.
class HomeData {
  HomeData({
    required this.banners,
    required this.newArrivals,
    required this.popular,
    required this.featured,
    required this.sections,
  });

  factory HomeData.fromJson(Map<String, dynamic> j) {
    List<Product> products(Object? v) => _list(v).map(Product.fromJson).toList();
    return HomeData(
      banners: _list(j['banners']).map(ShopBanner.fromJson).toList(),
      newArrivals: products(j['newArrivalItems']),
      popular: products(j['popularItems']),
      featured: products(j['featuredItems']),
      sections: _list(j['homeSections'])
          .map(
            (s) => HomeSection(
              categoryId: s['categoryId'] as int? ?? 0,
              name: s['name'] as String? ?? '',
              products: products(s['products']),
            ),
          )
          .toList(),
    );
  }

  final List<ShopBanner> banners;
  final List<Product> newArrivals;
  final List<Product> popular;
  final List<Product> featured;
  final List<HomeSection> sections;
}

/// A size chart: measurement names across the top, one row per size.
class SizeChart {
  SizeChart({required this.columns, required this.rows});

  static SizeChart? maybe(Object? v) {
    if (v is! Map<String, dynamic>) return null;
    final rows = _list(v['rows'])
        .map(
          (r) => SizeChartRow(
            size: r['size'] as String? ?? '',
            cells: _list(r['cells']).map((c) => (cm: _numOrNull(c['cm']), inch: _numOrNull(c['inch']))).toList(),
          ),
        )
        .toList();
    if (rows.isEmpty) return null;
    return SizeChart(columns: ((v['columns'] as List?) ?? const []).whereType<String>().toList(), rows: rows);
  }

  final List<String> columns;
  final List<SizeChartRow> rows;
}

class SizeChartRow {
  SizeChartRow({required this.size, required this.cells});
  final String size;
  final List<({double? cm, double? inch})> cells;
}

class Review {
  Review({required this.userName, required this.rating, required this.comment, this.createdAt});

  factory Review.fromJson(Map<String, dynamic> j) => Review(
    userName: j['userName'] as String? ?? 'Customer',
    rating: j['rating'] as int? ?? 0,
    comment: j['comment'] as String? ?? '',
    createdAt: DateTime.tryParse(j['createdAt'] as String? ?? ''),
  );

  final String userName;
  final int rating;
  final String comment;
  final DateTime? createdAt;
}

/// One page of a longer list.
class PageOf<T> {
  PageOf({required this.items, required this.total, required this.isLast});
  final List<T> items;
  final int total;
  final bool isLast;
}

/// A delivery area with its charge (Inside Dhaka, Outside Dhaka).
class ShippingZone {
  ShippingZone({required this.id, required this.name, required this.fee, required this.isDefault});

  factory ShippingZone.fromJson(Map<String, dynamic> j) => ShippingZone(
    id: j['id'] as int,
    name: j['displayName'] as String? ?? '',
    fee: _num(j['flatFee']),
    isDefault: j['isDefault'] as bool? ?? false,
  );

  final int id;
  final String name;
  final double fee;
  final bool isDefault;
}

/// The server's price for the bag: what the customer will actually pay.
class CheckoutQuote {
  CheckoutQuote({
    required this.subtotal,
    required this.shippingFee,
    required this.discount,
    required this.total,
    this.customSurcharge = 0,
    this.couponApplied = false,
    this.couponMessage,
    this.freeShippingReason,
    this.freeShippingOfferMin,
    this.allowedPaymentMethods,
    this.paymentMessage,
  });

  factory CheckoutQuote.fromJson(Map<String, dynamic> j) => CheckoutQuote(
    subtotal: _num(j['subtotal']),
    shippingFee: _num(j['shippingFee']),
    discount: _num(j['discountAmount']),
    total: _num(j['total']),
    customSurcharge: _num(j['customSurcharge']),
    couponApplied: j['couponApplied'] as bool? ?? false,
    couponMessage: j['couponMessage'] as String?,
    freeShippingReason: j['freeShippingReason'] as String?,
    freeShippingOfferMin: _numOrNull(j['freeShippingOfferMin']),
    allowedPaymentMethods: (j['allowedPaymentMethods'] as List?)?.whereType<String>().toList(),
    paymentMessage: j['paymentMessage'] as String?,
  );

  final double subtotal;
  final double shippingFee;
  final double discount;
  final double total;
  final double customSurcharge;
  final bool couponApplied;
  final String? couponMessage;

  /// FREE_ALL, OFFER or COUPON when delivery is free.
  final String? freeShippingReason;
  final double? freeShippingOfferMin;

  /// Null from a server that doesn't narrow payment by product.
  final List<String>? allowedPaymentMethods;

  /// Why no way to pay fits this bag.
  final String? paymentMessage;

  /// One line about free delivery, worded like the website's.
  String? get freeShippingNote {
    if (freeShippingReason == 'FREE_ALL') return 'Free delivery on all orders';
    final min = freeShippingOfferMin;
    if (min == null) return null;
    if (freeShippingReason == 'OFFER') return 'Your order ships free';
    final gap = min - (subtotal - discount);
    return gap > 0 ? 'Add ${_money(gap)} more for free delivery' : null;
  }
}

String _money(double v) => '৳${v == v.roundToDouble() ? v.round() : v.toStringAsFixed(2)}';

/// An order the server has saved.
class PlacedOrder {
  PlacedOrder({required this.id, required this.orderNumber, this.total, this.paymentMethod, this.bkashTrxId});

  factory PlacedOrder.fromJson(Map<String, dynamic> j) => PlacedOrder(
    id: '${j['id']}',
    orderNumber: j['orderNumber'] as String? ?? '${j['id']}',
    total: _numOrNull(j['totalAmount']),
    paymentMethod: j['paymentMethod'] as String?,
  );

  final String id;
  final String orderNumber;
  final double? total;

  /// CASH or BKASH.
  final String? paymentMethod;

  /// bKash's transaction id once the order is paid.
  final String? bkashTrxId;

  /// This order, paid with bKash.
  PlacedOrder paid(String? trxId) =>
      PlacedOrder(id: id, orderNumber: orderNumber, total: total, paymentMethod: 'BKASH', bkashTrxId: trxId ?? '');
}

/// A bKash payment as the shop's server reports it.
class BkashPayment {
  BkashPayment({
    required this.status,
    required this.paymentId,
    this.bkashUrl,
    this.trxId,
    this.amount,
    this.errorMessage,
  });

  factory BkashPayment.fromJson(Map<String, dynamic> j) => BkashPayment(
    status: j['status'] as String? ?? 'Failed',
    paymentId: j['paymentId'] as String? ?? '',
    bkashUrl: j['bkashURL'] as String?,
    trxId: j['trxId'] as String?,
    amount: _numOrNull(j['amount']),
    errorMessage: j['errorMessage'] as String?,
  );

  /// Initiated, Completed, Failed or Cancelled.
  final String status;
  final String paymentId;

  /// bKash's own payment page.
  final String? bkashUrl;

  /// bKash's transaction id, once paid.
  final String? trxId;
  final double? amount;
  final String? errorMessage;

  bool get isPaid => status == 'Completed';
}

/// What the server answers to logging in, signing up or refreshing.
class AuthTokens {
  AuthTokens({
    required this.userId,
    required this.email,
    required this.firstName,
    required this.lastName,
    required this.emailVerified,
    required this.accessToken,
    required this.refreshToken,
    required this.expiresInSeconds,
  });

  factory AuthTokens.fromJson(Map<String, dynamic> j) => AuthTokens(
    userId: '${j['userId']}',
    email: j['email'] as String? ?? '',
    firstName: j['firstName'] as String? ?? '',
    lastName: j['lastName'] as String? ?? '',
    emailVerified: j['emailVerified'] as bool? ?? false,
    accessToken: j['accessToken'] as String? ?? '',
    refreshToken: j['refreshToken'] as String? ?? '',
    expiresInSeconds: j['expiresInSeconds'] as int? ?? 300,
  );

  final String userId;
  final String email;
  final String firstName;
  final String lastName;
  final bool emailVerified;
  final String accessToken;
  final String refreshToken;
  final int expiresInSeconds;
}

/// The steps an order goes through, in order, with the words the website uses.
const orderSteps = ['PLACED', 'ACCEPTED', 'PROCESSING', 'SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERED'];

String orderStatusLabel(String status) => switch (status) {
  'PLACED' || 'PENDING' => 'Placed',
  'ACCEPTED' => 'Accepted',
  'IN_PRODUCTION' => 'In production',
  'PROCESSING' => 'Processing',
  'SHIPPED' => 'Shipped',
  'OUT_FOR_DELIVERY' => 'Out for delivery',
  'DELIVERED' => 'Delivered',
  'CANCELLED' => 'Cancelled',
  'RETURNED' => 'Returned',
  _ => status,
};

class OrderLine {
  OrderLine({required this.name, required this.quantity, required this.price, this.size, this.imageUrl});

  factory OrderLine.fromJson(Map<String, dynamic> j) => OrderLine(
    name: (j['productName'] ?? j['name']) as String? ?? '',
    quantity: j['quantity'] as int? ?? 1,
    price: _num(j['priceAtOrder'] ?? j['unitPrice']),
    size: (j['variantSize'] ?? j['size']) as String?,
    imageUrl: j['imageUrl'] as String?,
  );

  final String name;
  final int quantity;

  /// The price of one when it was ordered.
  final double price;

  /// "Regular Fit / M"
  final String? size;
  final String? imageUrl;
}

/// One of a logged-in customer's orders.
class Order {
  Order({
    required this.id,
    required this.orderNumber,
    required this.status,
    required this.total,
    required this.paymentMethod,
    required this.lines,
    this.createdAt,
    this.deliveryAddress,
    this.deliveryCharge = 0,
    this.paymentState,
    this.bkashTrxId,
    this.refunded,
    this.courierName,
    this.trackingNumber,
    this.cancellationReason,
  });

  factory Order.fromJson(Map<String, dynamic> j) => Order(
    id: '${j['id']}',
    orderNumber: j['orderNumber'] as String? ?? '${j['id']}',
    status: j['orderStatus'] as String? ?? 'PLACED',
    total: _num(j['totalAmount']),
    paymentMethod: j['paymentMethod'] as String? ?? 'CASH',
    lines: _list(j['orderItems']).map(OrderLine.fromJson).toList(),
    createdAt: DateTime.tryParse(j['createdAt'] as String? ?? '')?.toLocal(),
    deliveryAddress: j['deliveryAddress'] as String?,
    deliveryCharge: _num(j['deliveryCharge']),
    paymentState: j['paymentState'] as String?,
    bkashTrxId: j['bkashTrxId'] as String?,
    refunded: _numOrNull(j['bkashRefunded']),
    courierName: j['courierName'] as String?,
    trackingNumber: j['trackingNumber'] as String?,
    cancellationReason: j['cancellationReason'] as String?,
  );

  final String id;
  final String orderNumber;
  final String status;
  final double total;
  final String paymentMethod;
  final List<OrderLine> lines;
  final DateTime? createdAt;
  final String? deliveryAddress;
  final double deliveryCharge;

  /// bKash orders: PAID, FAILED or PENDING.
  final String? paymentState;
  final String? bkashTrxId;
  final double? refunded;
  final String? courierName;
  final String? trackingNumber;
  final String? cancellationReason;

  bool get isBkash => paymentMethod.toUpperCase() == 'BKASH';

  /// A bKash order still waiting for its money that can still be paid.
  bool get canPayNow => isBkash && paymentState != 'PAID' && (status == 'PLACED' || status == 'PENDING');

  PlacedOrder get asPlaced => PlacedOrder(id: id, orderNumber: orderNumber, total: total, paymentMethod: paymentMethod);
}

/// When an order reached each step, and what the customer may do next.
class OrderProgress {
  OrderProgress({required this.status, required this.reached, required this.canCancel});

  factory OrderProgress.fromJson(Map<String, dynamic> j) => OrderProgress(
    status: j['currentStatus'] as String? ?? 'PLACED',
    reached: {
      for (final h in _list(j['history']))
        if (DateTime.tryParse(h['createdAt'] as String? ?? '') case final at?)
          h['toStatus'] as String? ?? '': at.toLocal(),
    },
    canCancel: ((j['allowedCustomerNextStates'] as List?) ?? const []).contains('CANCELLED'),
  );

  final String status;

  /// Step → when the order got there.
  final Map<String, DateTime> reached;
  final bool canCancel;
}

/// An order looked up by its number: progress and items, nothing about the person.
class PublicTracking {
  PublicTracking({
    required this.orderNumber,
    required this.status,
    required this.lines,
    required this.reached,
    this.total,
    this.shipping,
    this.paymentMethod,
    this.paymentState,
    this.courierName,
    this.trackingNumber,
    this.refunded,
  });

  factory PublicTracking.fromJson(Map<String, dynamic> j) => PublicTracking(
    orderNumber: j['orderNumber'] as String? ?? '',
    status: j['status'] as String? ?? 'PLACED',
    lines: _list(j['items']).map(OrderLine.fromJson).toList(),
    reached: {
      for (final st in _list(j['steps']))
        if (DateTime.tryParse(st['at'] as String? ?? '') case final at?) st['status'] as String? ?? '': at.toLocal(),
      if (DateTime.tryParse(j['placedAt'] as String? ?? '') case final at?) 'PLACED': at.toLocal(),
    },
    total: _numOrNull(j['total']),
    shipping: _numOrNull(j['shipping']),
    paymentMethod: j['paymentMethod'] as String?,
    paymentState: j['paymentState'] as String?,
    courierName: j['courierName'] as String?,
    trackingNumber: j['courierTrackingNumber'] as String?,
    refunded: _numOrNull(j['refunded']),
  );

  final String orderNumber;
  final String status;
  final List<OrderLine> lines;
  final Map<String, DateTime> reached;
  final double? total;
  final double? shipping;
  final String? paymentMethod;
  final String? paymentState;
  final String? courierName;
  final String? trackingNumber;
  final double? refunded;

  bool get canPayNow =>
      (paymentMethod ?? '').toUpperCase() == 'BKASH' &&
      paymentState != 'PAID' &&
      (status == 'PLACED' || status == 'PENDING');

  /// For paying again: known by its number only.
  PlacedOrder get asPlaced => PlacedOrder(id: '', orderNumber: orderNumber, total: total, paymentMethod: paymentMethod);
}

class Profile {
  Profile({required this.firstName, required this.lastName, required this.email, this.phone});

  factory Profile.fromJson(Map<String, dynamic> j) => Profile(
    firstName: j['firstName'] as String? ?? '',
    lastName: j['lastName'] as String? ?? '',
    email: j['email'] as String? ?? '',
    phone: j['phoneNumber'] as String?,
  );

  final String firstName;
  final String lastName;
  final String email;
  final String? phone;
}

/// One box of the highlights band.
class Highlight {
  Highlight({required this.icon, required this.value, required this.label, required this.description});

  factory Highlight.fromJson(Map<String, dynamic> j) => Highlight(
    icon: j['icon'] as String? ?? 'star',
    value: j['value'] as String? ?? '',
    label: j['label'] as String? ?? '',
    description: j['description'] as String? ?? '',
  );

  final String icon;
  final String value;
  final String label;
  final String description;
}

class SocialLink {
  SocialLink({required this.handle, this.url});
  final String handle;
  final String? url;
}

/// "Our story" (Admin → Settings): words, a button, social accounts and photos.
class BrandStory {
  BrandStory({
    this.eyebrow,
    this.heading,
    this.body,
    this.ctaLabel,
    this.ctaLink,
    this.socials = const [],
    this.images = const [],
  });

  factory BrandStory.fromJson(Map<String, dynamic> j) => BrandStory(
    eyebrow: j['eyebrow'] as String?,
    heading: j['heading'] as String?,
    body: j['body'] as String?,
    ctaLabel: j['ctaLabel'] as String?,
    ctaLink: j['ctaLink'] as String?,
    socials: [
      for (final s in _list(j['socials']))
        if ((s['handle'] as String? ?? '').isNotEmpty)
          SocialLink(handle: s['handle'] as String, url: s['url'] as String?),
    ],
    images: ((j['images'] as List?) ?? const []).whereType<String>().toList(),
  );

  final String? eyebrow;
  final String? heading;
  final String? body;
  final String? ctaLabel;
  final String? ctaLink;
  final List<SocialLink> socials;
  final List<String> images;
}

class FooterPromise {
  FooterPromise({required this.icon, required this.title, required this.description});

  factory FooterPromise.fromJson(Map<String, dynamic> j) => FooterPromise(
    icon: j['icon'] as String? ?? '',
    title: j['title'] as String? ?? '',
    description: j['description'] as String? ?? '',
  );

  final String icon;
  final String title;
  final String description;
}

/// One of the shop's own stores.
class ShopOutlet {
  ShopOutlet({required this.name, this.imageUrl, this.address, this.phone, this.mapUrl});

  factory ShopOutlet.fromJson(Map<String, dynamic> j) => ShopOutlet(
    name: j['name'] as String? ?? '',
    imageUrl: j['imageUrl'] as String?,
    address: j['address'] as String?,
    phone: j['phone'] as String?,
    mapUrl: j['mapUrl'] as String?,
  );

  final String name;
  final String? imageUrl;
  final String? address;
  final String? phone;
  final String? mapUrl;
}

/// A running flash sale: its name, when it ends and its products.
class FlashSale {
  FlashSale({
    required this.name,
    required this.endsAt,
    required this.products,
    this.label,
    this.isPercent = true,
    this.value = 0,
    this.couponCode,
  });

  factory FlashSale.fromJson(Map<String, dynamic> j) => FlashSale(
    name: j['name'] as String? ?? '',
    label: j['label'] as String?,
    endsAt: DateTime.tryParse(j['endsAt'] as String? ?? '')?.toLocal() ?? DateTime.now(),
    isPercent: j['discountType'] != 'FLAT',
    value: _num(j['discountValue']),
    couponCode: j['couponCode'] as String?,
    products: _list(j['products']).map(Product.fromJson).toList(),
  );

  final String name;
  final String? label;
  final DateTime endsAt;
  final bool isPercent;
  final double value;
  final String? couponCode;
  final List<Product> products;

  /// "20% OFF" or "৳50 OFF".
  String get badge => isPercent ? '${value.round()}% OFF' : '${_money(value)} OFF';
}
