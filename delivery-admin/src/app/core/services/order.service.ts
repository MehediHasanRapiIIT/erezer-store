import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  OrderResponse,
  OrderStatus,
  OrderStatusOption,
  OrderStatusUpdateRequest,
  OrderSummary,
  OrderTrackingResponse,
  PageResponse,
} from '../models/api.models';

// Re-export so existing imports keep compiling.
export type { OrderStatus } from '../models/api.models';

/**
 * An admin's correction to an order. Anything left out is not changed. `items`,
 * when given, is the whole order as it should now be: a line with an id is
 * already on the order, a line without one is being added, and a line that is
 * missing is being removed.
 */
export interface OrderEdit {
  customerName?: string;
  customerPhone?: string;
  customerEmail?: string;
  deliveryAddress?: string;
  shippingFee?: number;
  discountAmount?: number;
  items?: { id: string | null; productId: number | null; variantId: number | null; quantity: number; unitPrice: number | null }[];
}

@Injectable({ providedIn: 'root' })
export class OrderService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  getSummary(): Observable<OrderSummary> {
    return this.http.get<OrderSummary>(`${this.baseUrl}/admin/orders/summary`);
  }

  /**
   * One page of orders, filtered by the server. Dates are shop days (Dhaka);
   * `q` searches the order number, the customer's name, phone and email, and
   * the address, across every order.
   */
  getOrdersPaged(page: number, size: number, status?: string, fromDate?: string, toDate?: string,
                 excludeStatus?: string, q?: string, payment?: string): Observable<PageResponse<OrderResponse>> {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (status && status !== 'ALL') params['status'] = status;
    if (excludeStatus) params['excludeStatus'] = excludeStatus;
    if (fromDate) params['fromDate'] = fromDate;
    if (toDate) params['toDate'] = toDate;
    if (q) params['q'] = q;
    if (payment && payment !== 'ALL') params['payment'] = payment;
    return this.http.get<PageResponse<OrderResponse>>(`${this.baseUrl}/admin/orders/paged`, { params });
  }

  /**
   * Backwards-compatible signature. For richer payloads (note, courier,
   * tracking) call {@link updateOrderStatusFull}.
   */
  updateOrderStatus(orderId: string, status: OrderStatus): Observable<OrderResponse> {
    return this.updateOrderStatusFull(orderId, { status });
  }

  updateOrderStatusFull(orderId: string, payload: OrderStatusUpdateRequest): Observable<OrderResponse> {
    return this.http.patch<OrderResponse>(`${this.baseUrl}/admin/orders/${orderId}/status`, payload);
  }

  getTracking(orderId: string): Observable<OrderTrackingResponse> {
    return this.http.get<OrderTrackingResponse>(`${this.baseUrl}/admin/orders/${orderId}/track`);
  }

  getStatusOptions(): Observable<OrderStatusOption[]> {
    return this.http.get<OrderStatusOption[]>(`${this.baseUrl}/admin/orders/statuses`);
  }

  /** Admin: fetch one order fresh (enriched customer + items). */
  getAdminOrder(orderId: string): Observable<OrderResponse> {
    return this.http.get<OrderResponse>(`${this.baseUrl}/admin/orders/${orderId}`);
  }

  /**
   * Corrects an order: customer details, items and quantities, and (with the
   * "Change prices on an order" permission) prices. The server moves stock for
   * every item that changed and answers with the order as it now is.
   */
  editOrder(orderId: string, edit: OrderEdit): Observable<OrderResponse> {
    return this.http.put<OrderResponse>(`${this.baseUrl}/admin/orders/${orderId}`, edit);
  }

  /** Invoice PDF (as a Blob) for viewing / printing. */
  getInvoicePdf(orderId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/admin/orders/${orderId}/invoice/pdf`, { responseType: 'blob' });
  }

  /** Emails the invoice to the customer + sends a short SMS. */
  sendInvoice(orderId: string): Observable<{ emailSent: boolean; smsSent: boolean; message: string }> {
    return this.http.post<{ emailSent: boolean; smsSent: boolean; message: string }>(
      `${this.baseUrl}/admin/orders/${orderId}/invoice/send`, {});
  }
}
