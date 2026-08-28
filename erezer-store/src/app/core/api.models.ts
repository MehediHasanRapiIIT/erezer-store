// ─── Auth ────────────────────────────────────────────────────────────────────

/** @deprecated phone+OTP login has been replaced with email/password. */
export interface PhoneLoginResponse {
  userId: string | null;
  phoneNumber: string;
  isNewUser: boolean;
}

/** @deprecated phone+OTP login has been replaced with email/password. */
export interface OtpVerifyResponse {
  userId: string;
  phoneNumber: string;
  token: string;
  isNewUser: boolean;
}

export interface RegisterPayload {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
}

export interface EmailLoginPayload {
  email: string;
  password: string;
}

export interface AuthTokenResponse {
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  emailVerified: boolean;
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  tokenType: string;
}

export interface MessageResponse {
  message: string;
}

// ─── Banner ───────────────────────────────────────────────────────────────────

export interface ApiBanner {
  id: string;
  imageUrl: string;
  fromDate: string;
  toDate: string;
  promotionTitle: string;
  promotionDetails: string;
}

// ─── Category ─────────────────────────────────────────────────────────────────

export interface ApiCategory {
  id: number;
  name: string;
  isActive: boolean;
  imageUrl?: string | null;
}

// ─── Product ──────────────────────────────────────────────────────────────────

export interface ApiProduct {
  id: number;
  categoryId: number;
  name: string;
  description: string;
  price: number;
  discountPrice: number;
  imageUrl: string;
  isAvailable: boolean;
  isNewArrival?: boolean | null;

  // Phase 3 — clothing brand fields
  brand?: string | null;
  gender?: string | null;
  material?: string | null;
  careInstructions?: string | null;
  avgRating?: number;
  totalReviews?: number;
  stockQuantity?: number;

  // Custom (made-to-order) sizing
  customSizeEnabled?: boolean | null;
  customSizeSurcharge?: number | null;
  customSizeNote?: string | null;
}

// ─── Variants & images (Phase 3) ────────────────────────────────────────────

export interface ApiVariant {
  id: number;
  productId: number;
  name: string | null;
  size: string | null;
  sku: string | null;
  stockQuantity: number | null;
  priceOverride: number | null;
}

export interface ApiProductImage {
  id: number;
  productId: number;
  url: string;
  altText: string | null;
  sortOrder: number;
  isPrimary: boolean;
}

export interface ApiStockStatus {
  productId: number;
  isAvailable?: boolean;        // original field (may be absent)
  stockStatus?: string;         // actual backend field: "IN_STOCK" | "OUT_OF_STOCK" | "LOW_STOCK"
  stockQuantity: number;
  lowStockThreshold?: number | null;
}

// ─── Home ─────────────────────────────────────────────────────────────────────

export interface ApiHomeData {
  banners: ApiBanner[];
  categories: ApiCategory[];
  popularItems: ApiProduct[];
  featuredItems: ApiProduct[];
  newArrivalItems: ApiProduct[];
}

// ─── Cart ─────────────────────────────────────────────────────────────────────

/** Standard envelope the backend wraps most non-auth responses in. */
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

/** Mirrors the backend CartItemDTO (the `data` payload of an ApiResponse). */
export interface ApiCartItem {
  cartItemId: string;
  productId: number;
  variantId: number | null;
  productName: string;
  imageUrl: string | null;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  stockStatus: string;
}

/** Mirrors the backend CartResponseDTO. */
export interface ApiCartResponse {
  items: ApiCartItem[];
  cartSubtotal: number;
  cartGrandTotal: number;
}

export interface ApiCartValidation {
  valid: boolean;
  invalidItems: Array<{
    cartItemId: number;
    productId: string;
    reason: string;
  }>;
}

// ─── Address ──────────────────────────────────────────────────────────────────

export type AddressType = 'HOME' | 'WORK' | 'OTHER';

export interface ApiAddress {
  id: number;
  name: string;
  address: string;
  latitude: number;
  longitude: number;
  houseNumber: number | null;
  apartmentName: string | null;
  addressType: AddressType;
  consumerId: string;
}

export interface AddressPayload {
  name: string;
  address: string;
  latitude: number;
  longitude: number;
  houseNumber: number | null;
  apartmentName: string | null;
  addressType: AddressType;
}

// ─── Profile ──────────────────────────────────────────────────────────────────

export interface ApiProfile {
  id: string;
  phoneNumber: string;
  isActive: boolean;
  firstName: string;
  lastName: string;
  email: string;
  profileImage: string | null;
  latitude: number | null;
  longitude: number | null;
  createdAt: string;
  updatedAt: string;
  addresses: ApiAddress[];
}

export interface ProfileUpdatePayload {
  firstName: string;
  lastName: string;
  email: string;
  latitude: number | null;
  longitude: number | null;
}

// ─── Reviews ──────────────────────────────────────────────────────────────────

export interface ApiReview {
  reviewId: string;
  userId: string;
  userName: string;
  rating: number;
  comment: string;
  createdAt: string;
}

export interface ApiReviewPage {
  content: ApiReview[];
  pageable: { pageNumber: number; pageSize: number };
  totalElements: number;
  totalPages: number;
  last: boolean;
  first: boolean;
}

export interface ApiRatingSummary {
  productId: number;
  avgRating: number;
  totalReviews: number;
  starBreakdown: Record<string, number>;
}

export interface SubmitReviewPayload {
  userId: string;
  orderId: string;
  rating: number;
  comment: string;
}

export interface UpdateReviewPayload {
  userId: string;
  rating: number;
  comment: string;
}

// ─── Checkout / Order Create ──────────────────────────────────────────────────

export interface OrderItemPayload {
  productId: number;
  quantity: number;
  variantId: number | null;
  /** JSON of custom (made-to-order) measurements; present only for custom lines. */
  customMeasurements?: string | null;
}

export interface CreateOrderPayload {
  deliveryAddress: string;
  phone?: string;
  paymentMethod: string;
  shopId: number;
  deliveryCharge: number;
  latitude: number | null;
  longitude: number | null;
  items: OrderItemPayload[];
  // Phase 4 additions
  couponCode?: string;
  shippingZoneId?: number;
  /** When set, the items are priced as this bundle offer (server-enforced). */
  bundleId?: string;
}

// ─── Phase 4: shipping, coupons, quote, bKash ───────────────────────────────

export interface ShippingZone {
  id: number;
  code: string;
  displayName: string;
  countryCode: string;
  flatFee: number;
  freeAbove: number | null;
  isDefault: boolean;
  isActive: boolean;
}

export type CouponDiscountType = 'PERCENT' | 'FLAT' | 'FREE_SHIPPING';

export interface CouponValidateRequest {
  code: string;
  cartSubtotal: number;
  userId?: string;
}

export interface CouponValidateResponse {
  valid: boolean;
  code: string | null;
  discountType: CouponDiscountType | null;
  discountAmount: number;
  removesShipping: boolean;
  reason: string | null;
}

export interface CheckoutQuoteRequest {
  items: OrderItemPayload[];
  shippingZoneId?: number;
  deliveryAddress?: string;
  couponCode?: string;
  userId?: string;
  /** When set, the quote prices the items as this bundle offer. */
  bundleId?: string;
}

// ─── Bundle offers ("Buy X Get Y") ───────────────────────────────────────────

/** A curated product a customer can pick for a bundle slot. */
export interface BundleProduct {
  id: number;
  name: string;
  price: number;
  discountPrice: number;
  imageUrl: string | null;
  stockQuantity: number;
}

export interface ApiBundleOffer {
  id: string;
  name: string;
  label: string | null;
  description: string | null;
  buyCount: number;
  getCount: number;
  slots: number;
  bundlePrice: number;
  compareAtPrice: number | null;
  savings: number | null;
  isActive: boolean;
  featured: boolean;
  sortOrder: number;
  images: string[];
  products: BundleProduct[];
}

export interface CheckoutQuoteResponse {
  subtotal: number;
  shippingFee: number;
  taxAmount: number;
  discountAmount: number;
  customSurcharge?: number | null;
  total: number;
  shippingZoneId: number | null;
  shippingZoneName: string | null;
  couponCode: string | null;
  couponDiscountType: CouponDiscountType | null;
  couponMessage: string | null;
  couponApplied: boolean;
}

export interface BkashPaymentResponse {
  status: 'Initiated' | 'Completed' | 'Failed' | 'Cancelled';
  paymentId: string;
  bkashURL: string;
  trxId: string | null;
  amount: number;
  currency: string;
  merchantInvoiceNumber: string;
  errorMessage: string | null;
}

// ─── Phase 5: returns + contact ─────────────────────────────────────────────

export type ReturnStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'PICKED_UP' | 'REFUNDED';

export type ReturnReason =
  | 'WRONG_SIZE'
  | 'DEFECTIVE'
  | 'NOT_AS_DESCRIBED'
  | 'CHANGED_MIND'
  | 'OTHER';

export type ReturnItemCondition = 'SEALED' | 'OPENED' | 'DAMAGED' | 'OTHER';

export interface ReturnItemRequest {
  orderItemId: string;
  quantity: number;
  condition?: ReturnItemCondition;
}

export interface ReturnRequestPayload {
  reason: ReturnReason;
  customerNotes?: string;
  items: ReturnItemRequest[];
}

export interface ReturnItem {
  id: string;
  orderItemId: string;
  productId: number | null;
  productName: string | null;
  quantity: number;
  condition: ReturnItemCondition | null;
  lineRefundAmount: number | null;
}

export interface ReturnPhoto {
  id: number;
  url: string;
  caption: string | null;
}

export interface ReturnRequestResponse {
  id: string;
  orderId: string;
  userId: string | null;
  customerEmail: string | null;
  status: ReturnStatus;
  reason: ReturnReason;
  customerNotes: string | null;
  adminNotes: string | null;
  refundAmount: number | null;
  requestedAt: string;
  decidedAt: string | null;
  decidedBy: string | null;
  pickedUpAt: string | null;
  refundedAt: string | null;
  items: ReturnItem[];
  photos: ReturnPhoto[];
}

export interface ContactMessagePayload {
  name: string;
  email: string;
  subject?: string;
  message: string;
  orderId?: string;
}

// ─── Phase 6: newsletter ─────────────────────────────────────────────────────

export interface NewsletterSubscribePayload {
  email: string;
  source?: string;
}

// ─── Orders ───────────────────────────────────────────────────────────────────

export interface ApiOrderItem {
  id: string;
  productId: number;
  productName: string;
  imageUrl: string | null;
  quantity: number;
  priceAtOrder: number;
  variantId: number | null;
  variantName: string | null;
  variantSize: string | null;
}

export interface ApiOrder {
  id: string;
  clientId: string;
  riderId: string | null;
  deliveryAddress: string;
  totalAmount: number;
  paymentMethod: string;
  paymentId: string | null;
  orderStatus: string;
  shopId: number | null;
  createdAt: string;
  deliveryCharge: number;
  latitude: number | null;
  longitude: number | null;
  orderItems: ApiOrderItem[];

  // Phase 2 additive fields
  courierName?: string | null;
  trackingNumber?: string | null;
  cancellationReason?: string | null;
  cancelledAt?: string | null;
  customerEmail?: string | null;
  customerName?: string | null;
  customerPhone?: string | null;
}

// ─── Order status (Phase 2) ──────────────────────────────────────────────────

export type OrderStatus =
  | 'PLACED'
  | 'ACCEPTED'
  | 'IN_PRODUCTION'
  | 'PROCESSING'
  | 'SHIPPED'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'RETURNED'
  /** @deprecated legacy alias for PLACED */
  | 'PENDING';

export interface OrderStatusHistoryEntry {
  id: string;
  orderId: string;
  fromStatus: string | null;
  toStatus: string;
  note: string | null;
  changedBy: string | null;
  createdAt: string;
}

export interface OrderTracking {
  orderId: string;
  currentStatus: OrderStatus;
  courierName: string | null;
  trackingNumber: string | null;
  history: OrderStatusHistoryEntry[];
  allowedCustomerNextStates: OrderStatus[];
}

export interface CancelOrderPayload {
  reason?: string;
}

export interface GuestOrderPayload {
  email: string;
  firstName: string;
  lastName: string;
  deliveryAddress: string;
  paymentMethod: string;
  shopId: number;
  shippingFee: number;
  items: OrderItemPayload[];
}

export interface AdminStatusUpdatePayload {
  status: OrderStatus;
  note?: string;
  courierName?: string;
  trackingNumber?: string;
}

// ─── Store settings & active discounts (Phase 9) ────────────────────────────

export interface ApiSizeChartCell {
  cm: number | null;
  inch: number | null;
}

export interface ApiSizeChartRow {
  size: string;
  cells: ApiSizeChartCell[];
}

export interface ApiSizeChart {
  columns: string[];
  rows: ApiSizeChartRow[];
}

export interface ApiBrandStory {
  eyebrow: string | null;
  heading: string | null;
  body: string | null;
  ctaLabel: string | null;
  ctaLink: string | null;
  socialHandle: string | null;
  socialUrl: string | null;
  images: string[];
}

export interface ApiFooterLink {
  label: string;
  url: string;
}

export interface ApiFooterColumn {
  title: string;
  links: ApiFooterLink[];
}

export interface ApiFooterPromise {
  icon: string | null;
  title: string | null;
  description: string | null;
}

export interface ApiFooterOutlet {
  imageUrl: string | null;
  name: string | null;
  address: string | null;
  phone: string | null;
}

export interface ApiFooter {
  brandName: string | null;
  blurb: string | null;
  columns: ApiFooterColumn[];
  promises: ApiFooterPromise[] | null;
  outlets: ApiFooterOutlet[] | null;
  copyright: string | null;
  tagline: string | null;
}

export interface ApiMarquee {
  enabled: boolean;
  items: string[];
}

export interface ApiHighlight {
  icon: string | null;
  value: string | null;
  label: string | null;
  description: string | null;
}

export interface ApiStoreSettings {
  returnPolicyText: string | null;
  exchangeWindowDays: number | null;
  supportPhone: string | null;
  supportEmail: string | null;
  supportHours: string | null;
  sizeChart: ApiSizeChart | null;
  brandStory: ApiBrandStory | null;
  footer: ApiFooter | null;
  marquee: ApiMarquee | null;
  highlights: ApiHighlight[] | null;
  paymentCodEnabled: boolean | null;
  paymentBkashEnabled: boolean | null;
  paymentCardEnabled: boolean | null;
}

export type ApiDiscountScope = 'PRODUCT' | 'CATEGORY' | 'GLOBAL';
export type ApiDiscountType = 'PERCENT' | 'FLAT';

export interface ApiActiveDiscount {
  id: string;
  name: string;
  scope: ApiDiscountScope;
  discountType: ApiDiscountType;
  discountValue: number | null;
  targetId: number | null;
  stackable: boolean;
  priority: number;
}

// ─── Flash sale (time-boxed promotional campaign) ────────────────────────────

/**
 * A single, currently-running flash-sale campaign surfaced by
 * {@code GET /api/flash-sale}. The endpoint returns the active campaign, or an
 * empty body / 204 when none is running (the storefront then hides the widget
 * and the Flash Sale page falls back to an empty state).
 *
 * `discountType`/`discountValue` describe the headline offer ("20% OFF on all
 * items"); `products` are the items included in the sale, each carrying its own
 * `price`/`discountPrice` so cards can show the strike-through. `endsAt` is an
 * ISO-8601 instant that drives the live countdown. `couponCode`/`minSpend`
 * power the optional "copy code" banner.
 */
export interface ApiFlashSale {
  id: string;
  name: string;                       // e.g. "Black Friday"
  label: string | null;              // small eyebrow, e.g. "Limited time"
  discountType: ApiDiscountType;     // PERCENT | FLAT
  discountValue: number;             // 20 (percent) or 50 (flat ৳)
  endsAt: string;                    // ISO-8601 — countdown target
  startsAt?: string | null;
  couponCode?: string | null;        // optional "copy code" pill
  minSpend?: number | null;          // optional min order for the coupon
  featured?: boolean | null;         // shown in the landing-page widget
  products: ApiProduct[];
}

// ─── Custom design studio ────────────────────────────────────────────────────

/** The four garment mockup images (one per canvas view) for a colourway. */
export interface CustomDesignImages {
  front: string | null;
  back: string | null;
  leftSleeve: string | null;
  rightSleeve: string | null;
}

export interface CustomDesignColor {
  name: string;
  hex: string;
  images: CustomDesignImages;
}

export interface CustomDesignItem {
  name: string;
  category: string | null;
  sizes: string[];
  printTechniques: string[];
  colors: CustomDesignColor[];
}

export interface CustomDesignLogo {
  name: string;
  url: string;
}

/** Response of GET /api/custom-design/assets — everything the studio renders. */
export interface CustomDesignAssets {
  items: CustomDesignItem[];
  logos: CustomDesignLogo[];
}

/** The four canvas views, in display order. */
export type CustomDesignView = 'front' | 'back' | 'leftSleeve' | 'rightSleeve';

/** JSON part of the multipart "Submit for Price" request. */
export interface CustomOrderRequest {
  firstName: string;
  lastName: string;
  phone: string;
  email: string;
  shippingAddress: string;
  apartment?: string;
  city: string;
  zipCode?: string;
  country: string;
  notes: string;                 // rich-text HTML (size-wise qty + technique)
  itemName?: string;
  colorName?: string;
  size?: string;
  printTechnique?: string;
  designJson?: string;           // serialized per-view fabric state
}

export interface CustomOrderImageRef {
  view: string;
  url: string;
}

/** Response of POST /api/custom-design/requests. */
export interface CustomOrderResponse {
  id: string;
  reference: string;
  status: string;
  images: CustomOrderImageRef[];
}

export interface CustomDesignDraft {
  id: string;
  name: string;
  itemName: string | null;
  colorName: string | null;
  thumbnailUrl: string | null;
  designJson: string | null;
  shareToken: string | null;
  updatedAt: string | null;
}

export interface SaveDraftPayload {
  name: string;
  itemName?: string;
  colorName?: string;
  thumbnailUrl?: string;
  designJson: string;
}
