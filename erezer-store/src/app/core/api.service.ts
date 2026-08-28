import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import {
  AddressPayload,
  ApiAddress,
  ApiBanner,
  ApiCartItem,
  ApiCartResponse,
  ApiActiveDiscount,
  ApiFlashSale,
  ApiResponse,
  ApiCartValidation,
  ApiCategory,
  ApiHomeData,
  ApiOrder,
  ApiProduct,
  ApiProductImage,
  ApiProfile,
  ApiRatingSummary,
  ApiReview,
  ApiReviewPage,
  ApiStockStatus,
  ApiStoreSettings,
  ApiVariant,
  AuthTokenResponse,
  BkashPaymentResponse,
  CancelOrderPayload,
  CheckoutQuoteRequest,
  CheckoutQuoteResponse,
  ContactMessagePayload,
  CouponValidateRequest,
  CouponValidateResponse,
  CustomDesignAssets,
  CustomDesignDraft,
  CustomOrderRequest,
  CustomOrderResponse,
  SaveDraftPayload,
  ApiBundleOffer,
  CreateOrderPayload,
  EmailLoginPayload,
  GuestOrderPayload,
  MessageResponse,
  OrderTracking,
  OtpVerifyResponse,
  PhoneLoginResponse,
  ProfileUpdatePayload,
  RegisterPayload,
  ReturnRequestPayload,
  ReturnRequestResponse,
  ShippingZone,
  SubmitReviewPayload,
  UpdateReviewPayload,
} from './api.models';

const BASE = 'http://localhost:8080';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  // ─── Auth (email + password) ───────────────────────────────────────────────

  register(payload: RegisterPayload): Observable<AuthTokenResponse> {
    return this.http.post<AuthTokenResponse>(`${BASE}/app/auth/register`, payload);
  }

  login(payload: EmailLoginPayload): Observable<AuthTokenResponse> {
    return this.http.post<AuthTokenResponse>(`${BASE}/app/auth/login`, payload);
  }

  refreshToken(refreshToken: string): Observable<AuthTokenResponse> {
    return this.http.post<AuthTokenResponse>(`${BASE}/app/auth/refresh`, { refreshToken });
  }

  verifyEmail(token: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${BASE}/app/auth/verify-email`, { token });
  }

  resendVerification(email: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${BASE}/app/auth/resend-verification`, { email });
  }

  forgotPassword(email: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${BASE}/app/auth/forgot-password`, { email });
  }

  resetPassword(token: string, newPassword: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${BASE}/app/auth/reset-password`, { token, newPassword });
  }

  /** @deprecated kept for transition; use login() */
  phoneLogin(phoneNumber: string): Observable<PhoneLoginResponse> {
    return this.http.post<PhoneLoginResponse>(`${BASE}/app/auth/phone/login`, { phoneNumber });
  }

  /** @deprecated kept for transition; use login() */
  verifyOtp(phoneNumber: string, otpCode: string): Observable<OtpVerifyResponse> {
    return this.http.post<OtpVerifyResponse>(`${BASE}/app/auth/otp/verify`, { phoneNumber, otpCode });
  }

  // ─── Home ──────────────────────────────────────────────────────────────────

  getHomeData(): Observable<ApiHomeData> {
    return this.http.get<ApiHomeData>(`${BASE}/app/home`);
  }

  // ─── Banners ───────────────────────────────────────────────────────────────

  getBanners(): Observable<ApiBanner[]> {
    return this.http.get<ApiBanner[]>(`${BASE}/api/banners`);
  }

  // ─── Categories ────────────────────────────────────────────────────────────

  getCategories(): Observable<ApiCategory[]> {
    return this.http.get<ApiCategory[]>(`${BASE}/api/categories`);
  }

  getProductsByCategory(categoryId: number): Observable<ApiProduct[]> {
    return this.http.get<ApiProduct[]>(`${BASE}/api/categories/${categoryId}/products`);
  }

  // ─── Products ──────────────────────────────────────────────────────────────

  getProducts(): Observable<ApiProduct[]> {
    return this.http.get<ApiProduct[]>(`${BASE}/api/products`);
  }

  getProductById(id: number): Observable<ApiProduct> {
    return this.http.get<ApiProduct>(`${BASE}/api/products/${id}`);
  }

  searchProducts(name: string): Observable<ApiProduct[]> {
    return this.http.get<ApiProduct[]>(`${BASE}/api/products/search`, { params: { name } });
  }

  getProductStockStatus(id: number): Observable<ApiStockStatus> {
    return this.http.get<ApiStockStatus>(`${BASE}/api/products/${id}/stock-status`);
  }

  getProductVariants(id: number): Observable<ApiVariant[]> {
    return this.http.get<ApiVariant[]>(`${BASE}/api/products/${id}/variants`);
  }

  getProductImages(id: number): Observable<ApiProductImage[]> {
    return this.http.get<ApiProductImage[]>(`${BASE}/api/products/${id}/images`);
  }

  getRelatedProducts(id: number, limit = 8): Observable<ApiProduct[]> {
    return this.http.get<ApiProduct[]>(`${BASE}/api/products/${id}/related`, {
      params: { limit: String(limit) }
    });
  }

  // ─── Store settings & active discounts (Phase 9) ───────────────────────────

  getStoreSettings(): Observable<ApiStoreSettings> {
    return this.http.get<ApiStoreSettings>(`${BASE}/api/store-settings`);
  }

  getActiveDiscounts(): Observable<ApiActiveDiscount[]> {
    return this.http.get<ApiActiveDiscount[]>(`${BASE}/api/discounts/active`);
  }

  /**
   * The currently-running flash sale, or null when none is active (the backend
   * may answer 204/empty — normalise that to null so callers can `@if` on it).
   */
  getFlashSale(): Observable<ApiFlashSale | null> {
    return this.http.get<ApiFlashSale | null>(`${BASE}/api/flash-sale`)
      .pipe(map((sale) => (sale && sale.id ? sale : null)));
  }

  /** All currently-active flash sales (for the "view all deals" list page). */
  getFlashSales(): Observable<ApiFlashSale[]> {
    return this.http.get<ApiFlashSale[]>(`${BASE}/api/flash-sales`);
  }

  /** One active flash sale by id (for its detail page). */
  getFlashSaleById(id: string): Observable<ApiFlashSale> {
    return this.http.get<ApiFlashSale>(`${BASE}/api/flash-sales/${id}`);
  }

  // ─── Cart ──────────────────────────────────────────────────────────────────

  // Cart endpoints are wrapped in the ApiResponse envelope — unwrap `.data`.
  getCart(userId: string): Observable<ApiCartItem[]> {
    return this.http.get<ApiResponse<ApiCartResponse>>(`${BASE}/app/consumer/${userId}/cart`)
      .pipe(map((r) => r.data?.items ?? []));
  }

  addToCart(
    userId: string,
    payload: { userId: string; productId: string; variantId: number | null; quantity: number; deliveryInstructions: string | null }
  ): Observable<ApiCartItem> {
    return this.http.post<ApiResponse<ApiCartItem>>(`${BASE}/app/consumer/${userId}/cart`, payload)
      .pipe(map((r) => r.data));
  }

  // Backend exposes PATCH increment/decrement (no PUT-with-quantity endpoint).
  incrementCartItem(userId: string, cartItemId: string): Observable<ApiCartItem> {
    return this.http.patch<ApiResponse<ApiCartItem>>(`${BASE}/app/consumer/${userId}/cart/${cartItemId}/increment`, {})
      .pipe(map((r) => r.data));
  }

  decrementCartItem(userId: string, cartItemId: string): Observable<ApiCartItem | null> {
    return this.http.patch<ApiResponse<ApiCartItem>>(`${BASE}/app/consumer/${userId}/cart/${cartItemId}/decrement`, {})
      .pipe(map((r) => r.data ?? null));
  }

  removeCartItem(userId: string, cartItemId: string): Observable<void> {
    return this.http.delete<ApiResponse<void>>(`${BASE}/app/consumer/${userId}/cart/${cartItemId}`)
      .pipe(map(() => undefined));
  }

  updateDeliveryInstructions(userId: string, cartItemId: string, deliveryInstructions: string): Observable<ApiCartItem> {
    return this.http.patch<ApiResponse<ApiCartItem>>(
      `${BASE}/app/consumer/${userId}/cart/${cartItemId}/delivery-instructions`,
      { deliveryInstructions }
    ).pipe(map((r) => r.data));
  }

  validateCartStock(userId: string): Observable<ApiCartValidation> {
    return this.http.post<ApiCartValidation>(`${BASE}/app/consumer/${userId}/cart/validate-stock`, null);
  }

  // ─── Addresses ─────────────────────────────────────────────────────────────

  getAddresses(userId: string): Observable<ApiAddress[]> {
    return this.http.get<ApiAddress[]>(`${BASE}/app/consumer/${userId}/addresses`);
  }

  addAddress(userId: string, payload: AddressPayload): Observable<ApiAddress> {
    return this.http.post<ApiAddress>(`${BASE}/app/consumer/${userId}/addresses`, payload);
  }

  updateAddress(userId: string, addressId: number, payload: AddressPayload): Observable<ApiAddress> {
    return this.http.put<ApiAddress>(`${BASE}/app/consumer/${userId}/addresses/${addressId}`, payload);
  }

  deleteAddress(userId: string, addressId: number): Observable<void> {
    return this.http.delete<void>(`${BASE}/app/consumer/${userId}/addresses/${addressId}`);
  }

  // ─── Profile ───────────────────────────────────────────────────────────────

  getProfile(userId: string): Observable<ApiProfile> {
    return this.http.get<ApiProfile>(`${BASE}/app/consumer/${userId}/profile`);
  }

  updateProfile(userId: string, payload: ProfileUpdatePayload): Observable<ApiProfile> {
    return this.http.put<ApiProfile>(`${BASE}/app/consumer/${userId}/profile`, payload);
  }

  // ─── Reviews ───────────────────────────────────────────────────────────────

  getReviews(productId: number, page = 0, size = 10): Observable<ApiReviewPage> {
    return this.http.get<ApiReviewPage>(`${BASE}/api/products/${productId}/reviews`, {
      params: { page: String(page), size: String(size) }
    });
  }

  submitReview(productId: number, payload: SubmitReviewPayload): Observable<ApiReview> {
    return this.http.post<ApiReview>(`${BASE}/api/products/${productId}/reviews`, payload);
  }

  updateReview(productId: number, reviewId: string, payload: UpdateReviewPayload): Observable<ApiReview> {
    return this.http.put<ApiReview>(`${BASE}/api/products/${productId}/reviews/${reviewId}`, payload);
  }

  deleteReview(productId: number, reviewId: string, userId: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/api/products/${productId}/reviews/${reviewId}`, {
      params: { userId }
    });
  }

  getRatingSummary(productId: number): Observable<ApiRatingSummary> {
    return this.http.get<ApiRatingSummary>(`${BASE}/api/products/${productId}/rating-summary`);
  }

  // ─── Orders ────────────────────────────────────────────────────────────────

  createOrder(userId: string, payload: CreateOrderPayload): Observable<ApiOrder> {
    return this.http.post<ApiOrder>(`${BASE}/app/consumer/${userId}/orders`, payload);
  }

  createGuestOrder(payload: GuestOrderPayload): Observable<ApiOrder> {
    return this.http.post<ApiOrder>(`${BASE}/app/consumer/guest/orders`, payload);
  }

  getOrders(userId: string): Observable<ApiOrder[]> {
    return this.http.get<ApiOrder[]>(`${BASE}/app/consumer/${userId}/orders`);
  }

  getOrderById(userId: string, orderId: string): Observable<ApiOrder> {
    return this.http.get<ApiOrder>(`${BASE}/app/consumer/${userId}/orders/${orderId}`);
  }

  cancelOrder(userId: string, orderId: string, payload: CancelOrderPayload = {}): Observable<ApiOrder> {
    return this.http.post<ApiOrder>(`${BASE}/app/consumer/${userId}/orders/${orderId}/cancel`, payload);
  }

  /** Edit an order's shipping address + phone (server allows only while PLACED). */
  updateOrderContact(userId: string, orderId: string, payload: { deliveryAddress: string; phone?: string }): Observable<ApiOrder> {
    return this.http.patch<ApiOrder>(`${BASE}/app/consumer/${userId}/orders/${orderId}/contact`, payload);
  }

  trackOrder(userId: string, orderId: string): Observable<OrderTracking> {
    return this.http.get<OrderTracking>(`${BASE}/app/consumer/${userId}/orders/${orderId}/track`);
  }

  // ─── Phase 4: shipping zones, coupons, checkout quote, bKash ──────────────

  getShippingZones(): Observable<ShippingZone[]> {
    return this.http.get<ShippingZone[]>(`${BASE}/api/shipping/zones`);
  }

  validateCoupon(payload: CouponValidateRequest): Observable<CouponValidateResponse> {
    return this.http.post<CouponValidateResponse>(`${BASE}/api/coupons/validate`, payload);
  }

  getCheckoutQuote(payload: CheckoutQuoteRequest): Observable<CheckoutQuoteResponse> {
    return this.http.post<CheckoutQuoteResponse>(`${BASE}/api/checkout/quote`, payload);
  }

  bkashInit(orderId: string): Observable<BkashPaymentResponse> {
    return this.http.post<BkashPaymentResponse>(`${BASE}/api/payments/bkash/init`, { orderId });
  }

  bkashExecute(paymentId: string): Observable<BkashPaymentResponse> {
    return this.http.post<BkashPaymentResponse>(`${BASE}/api/payments/bkash/execute`, { paymentId });
  }

  bkashQuery(paymentId: string): Observable<BkashPaymentResponse> {
    return this.http.get<BkashPaymentResponse>(`${BASE}/api/payments/bkash/query/${paymentId}`);
  }

  // ─── Phase 5: returns + contact ────────────────────────────────────────────

  /**
   * Creates a return request for an existing order. Photos (up to 3) are
   * optional File handles. The backend expects a multipart with a JSON `body`
   * part plus 0-3 image parts.
   */
  createReturnRequest(
    userId: string,
    orderId: string,
    payload: ReturnRequestPayload,
    photos: File[] = [],
  ): Observable<ReturnRequestResponse> {
    const form = new FormData();
    form.append('body', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
    for (const photo of photos.slice(0, 3)) {
      form.append('photos', photo);
    }
    const url = `${BASE}/app/consumer/${userId}/returns?orderId=${encodeURIComponent(orderId)}`;
    return this.http.post<ReturnRequestResponse>(url, form);
  }

  listMyReturns(userId: string): Observable<ReturnRequestResponse[]> {
    return this.http.get<ReturnRequestResponse[]>(`${BASE}/app/consumer/${userId}/returns`);
  }

  getMyReturn(userId: string, returnId: string): Observable<ReturnRequestResponse> {
    return this.http.get<ReturnRequestResponse>(
      `${BASE}/app/consumer/${userId}/returns/${returnId}`,
    );
  }

  submitContactMessage(payload: ContactMessagePayload): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${BASE}/api/support/contact`, payload);
  }

  // ─── Phase 6: newsletter ────────────────────────────────────────────────────

  subscribeNewsletter(email: string, source: string = 'STOREFRONT'): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${BASE}/api/newsletter/subscribe`, { email, source });
  }

  unsubscribeNewsletter(token: string): Observable<MessageResponse> {
    return this.http.get<MessageResponse>(`${BASE}/api/newsletter/unsubscribe`, {
      params: { token }
    });
  }

  // ─── Custom design studio ────────────────────────────────────────────────────

  getCustomDesignAssets(): Observable<CustomDesignAssets> {
    return this.http.get<CustomDesignAssets>(`${BASE}/api/custom-design/assets`);
  }

  // ─── Bundle offers ───────────────────────────────────────────────────────────

  getBundles(): Observable<ApiBundleOffer[]> {
    return this.http.get<ApiBundleOffer[]>(`${BASE}/api/bundles`);
  }

  getBundle(id: string): Observable<ApiBundleOffer> {
    return this.http.get<ApiBundleOffer>(`${BASE}/api/bundles/${id}`);
  }

  /** Featured bundle for the landing widget; null when none (204). */
  getFeaturedBundle(): Observable<ApiBundleOffer | null> {
    return this.http.get<ApiBundleOffer>(`${BASE}/api/bundle`, { observe: 'response' })
      .pipe(map((res) => res.body ?? null));
  }

  /** Uploads a customer's own artwork; returns its stored URL. */
  uploadCustomArtwork(file: File): Observable<{ url: string }> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<{ url: string }>(`${BASE}/api/custom-design/upload`, form);
  }

  /**
   * "Submit for Price". Sent as multipart: the request as a JSON `data` part,
   * plus one file part per non-empty view named after the view (e.g. front.png)
   * so the backend can label each preview.
   */
  submitCustomDesignRequest(
    payload: CustomOrderRequest,
    previews: { view: string; blob: Blob }[],
  ): Observable<CustomOrderResponse> {
    const form = new FormData();
    form.append('data', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
    for (const p of previews) {
      form.append('previews', p.blob, `${p.view}.png`);
    }
    return this.http.post<CustomOrderResponse>(`${BASE}/api/custom-design/requests`, form);
  }

  getSharedDesign(token: string): Observable<CustomDesignDraft> {
    return this.http.get<CustomDesignDraft>(`${BASE}/api/custom-design/shared/${token}`);
  }

  // Drafts (logged-in customers)

  listCustomDesignDrafts(userId: string): Observable<CustomDesignDraft[]> {
    return this.http.get<CustomDesignDraft[]>(
      `${BASE}/app/consumer/${userId}/custom-design/drafts`,
    );
  }

  getCustomDesignDraft(userId: string, draftId: string): Observable<CustomDesignDraft> {
    return this.http.get<CustomDesignDraft>(
      `${BASE}/app/consumer/${userId}/custom-design/drafts/${draftId}`,
    );
  }

  saveCustomDesignDraft(userId: string, payload: SaveDraftPayload): Observable<CustomDesignDraft> {
    return this.http.post<CustomDesignDraft>(
      `${BASE}/app/consumer/${userId}/custom-design/drafts`,
      payload,
    );
  }

  updateCustomDesignDraft(
    userId: string,
    draftId: string,
    payload: SaveDraftPayload,
  ): Observable<CustomDesignDraft> {
    return this.http.put<CustomDesignDraft>(
      `${BASE}/app/consumer/${userId}/custom-design/drafts/${draftId}`,
      payload,
    );
  }

  deleteCustomDesignDraft(userId: string, draftId: string): Observable<void> {
    return this.http.delete<void>(
      `${BASE}/app/consumer/${userId}/custom-design/drafts/${draftId}`,
    );
  }

  shareCustomDesignDraft(userId: string, draftId: string): Observable<CustomDesignDraft> {
    return this.http.post<CustomDesignDraft>(
      `${BASE}/app/consumer/${userId}/custom-design/drafts/${draftId}/share`,
      {},
    );
  }
}
