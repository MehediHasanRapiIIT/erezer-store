// Admin session stored after successful OTP verification
export interface AdminSession {
  userId: string;
  token: string;
  phoneNumber: string;
}

// --- Auth ---
export interface OtpGenerateRequest {
  phoneNumber: string;
}

export interface OtpVerifyRequest {
  phoneNumber: string;
  otpCode: string;
}

export interface OtpVerifyResponse {
  userId: string;
  phoneNumber: string;
  token: string;
  isNewUser: boolean;
}

// --- Products ---
export interface ProductRequest {
  categoryId: number;
  name: string;
  description: string;
  price: number;
  discountPercentage?: number;
  shopId: number;
  isAvailable: boolean;
  isNewArrival?: boolean;
  isFeatured?: boolean;
  unit?: string;
  lowStockThreshold?: number;
  brand?: string;
  gender?: string;
  material?: string;
  careInstructions?: string;
  // Custom (made-to-order) sizing
  customSizeEnabled?: boolean;
  customSizeSurcharge?: number | null;
  customSizeNote?: string | null;
}

export interface ProductResponse {
  id: number;
  categoryId: number;
  categoryName: string | null;
  sku: string | null;
  unit: string | null;
  name: string;
  description: string;
  price: number;
  discountPrice: number;
  imageUrl: string;
  isAvailable: boolean;
  isNewArrival: boolean | null;
  isFeatured: boolean | null;
  stockQuantity: number;
  stockStatus: 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK';
  avgRating: number;
  totalReviews: number;
  lowStockThreshold: number | null;
  brand: string | null;
  gender: string | null;
  material: string | null;
  careInstructions: string | null;
  // Custom (made-to-order) sizing
  customSizeEnabled: boolean | null;
  customSizeSurcharge: number | null;
  customSizeNote: string | null;
}

// --- Stock ---
export interface StockUpdateRequest {
  operation: 'SET' | 'INCREMENT' | 'DECREMENT';
  quantity: number;
  unit?: string;
  lowStockThreshold?: number;
}

export interface BulkStockItem {
  productId: number;
  quantity: number;
  unit?: string;
  lowStockThreshold?: number;
}

export interface BulkStockUpdateRequest {
  updates: BulkStockItem[];
}

export interface StockResponse {
  productId: number;
  productName: string;
  sku: string | null;
  imageUrl: string | null;
  unit: string;
  stockQuantity: number;
  stockStatus: 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK';
  lowStockThreshold: number | null;
}

export interface InventorySummary {
  criticalLow: number;
  outOfStock: number;
  reorderPending: number;
}

// --- Categories ---
export interface CategoryRequest {
  name: string;
  isActive: boolean;
  imageUrl?: string | null;
}

export interface CategoryResponse {
  id: number;
  name: string;
  isActive: boolean;
  imageUrl?: string | null;
  productCount: number;
}

// --- Banners ---
export interface BannerResponse {
  id: string;
  imageUrl: string;
  promotionTitle: string;
  promotionDetails: string;
  fromDate: string;
  toDate: string;
}

// --- Home / Dashboard ---
export interface HomeResponse {
  banners: BannerResponse[];
  categories: CategoryResponse[];
  popularItems: ProductResponse[];
  featuredItems: ProductResponse[];
}

// --- Orders ---
export interface OrderItem {
  id: string;
  orderId: string;
  productId: string | null;
  productName: string | null;
  imageUrl: string | null;
  quantity: number;
  priceAtOrder: number;
  variantId: number | null;
  variantName: string | null;
  variantSize: string | null;
  customMeasurements: string | null;
  customSurcharge: number | null;
}

export interface OrderResponse {
  id: string;
  clientId: string;
  deliveryAddress: string;
  totalAmount: number;
  paymentMethod: string;
  paymentId: string | null;
  orderStatus: string;
  shopId: number;
  createdAt: string;
  deliveryCharge: number;
  latitude: number;
  longitude: number;
  orderItems: OrderItem[];
  customerName: string | null;
  customerPhone: string | null;

  // Phase 2 additive fields
  courierName?: string | null;
  trackingNumber?: string | null;
  cancellationReason?: string | null;
  cancelledAt?: string | null;
  customerEmail?: string | null;
}

// ─── Order status & tracking (Phase 2) ──────────────────────────────────────

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
  /** @deprecated legacy initial state, alias for PLACED */
  | 'PENDING';

export interface OrderStatusUpdateRequest {
  status: OrderStatus;
  note?: string;
  courierName?: string;
  trackingNumber?: string;
}

export interface OrderStatusHistoryEntry {
  id: string;
  orderId: string;
  fromStatus: string | null;
  toStatus: string;
  note: string | null;
  changedBy: string | null;
  createdAt: string;
}

export interface OrderTrackingResponse {
  orderId: string;
  currentStatus: OrderStatus;
  courierName: string | null;
  trackingNumber: string | null;
  history: OrderStatusHistoryEntry[];
  allowedCustomerNextStates: OrderStatus[];
}

export interface OrderStatusOption {
  status: OrderStatus;
  allowedNext: OrderStatus[];
}

export interface OrderSummary {
  total: number;
  pending: number;
  outForDelivery: number;
  completed: number;
}

// --- Reviews ---
export interface ReviewResponse {
  reviewId: string;
  userId: string;
  userName: string;
  rating: number;
  comment: string;
  createdAt: string;
}

export interface RatingSummary {
  productId: number;
  avgRating: number;
  totalReviews: number;
  starBreakdown: { [key: number]: number };
}

// --- Pagination ---
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

// --- Global Search ---
export type SearchItemType =
  | 'PRODUCT'
  | 'CATEGORY'
  | 'ORDER'
  | 'CUSTOMER'
  | 'REVIEW'
  | 'SHOP'
  | 'BANNER';

export interface SearchItem {
  id: string;
  type: SearchItemType;
  title: string;
  subtitle?: string;
  imageUrl?: string;
  extra?: Record<string, unknown>;
}

export interface SearchGroup {
  items: SearchItem[];
  total: number;
}

export interface AdminSearchResponse {
  query: string;
  products: SearchGroup;
  categories: SearchGroup;
  orders: SearchGroup;
  customers: SearchGroup;
  reviews: SearchGroup;
  totalCount: number;
}

export interface CustomerSearchResponse {
  query: string;
  products: SearchGroup;
  categories: SearchGroup;
  shops: SearchGroup;
  banners: SearchGroup;
  totalCount: number;
}

// --- Error shapes ---
export interface ApiValidationError {
  errors: Record<string, string>;
  timestamp: string;
}

export interface ApiBusinessError {
  message: string;
  timestamp: string;
}
