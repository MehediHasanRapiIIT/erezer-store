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

@Injectable({ providedIn: 'root' })
export class OrderService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  getSummary(): Observable<OrderSummary> {
    return this.http.get<OrderSummary>(`${this.baseUrl}/admin/orders/summary`);
  }

  getAllOrders(): Observable<OrderResponse[]> {
    return this.http.get<OrderResponse[]>(`${this.baseUrl}/admin/orders`);
  }

  getOrdersPaged(page: number, size: number, status?: string, fromDate?: string, toDate?: string, excludeStatus?: string): Observable<PageResponse<OrderResponse>> {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (status && status !== 'ALL') params['status'] = status;
    if (excludeStatus) params['excludeStatus'] = excludeStatus;
    if (fromDate) params['fromDate'] = fromDate;
    if (toDate) params['toDate'] = toDate;
    return this.http.get<PageResponse<OrderResponse>>(`${this.baseUrl}/admin/orders/paged`, { params });
  }

  getOrdersByStatus(status: OrderStatus): Observable<OrderResponse[]> {
    return this.http.get<OrderResponse[]>(`${this.baseUrl}/admin/orders/status/${status}`);
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

  getOrdersByUser(userId: string): Observable<OrderResponse[]> {
    return this.http.get<OrderResponse[]>(`${this.baseUrl}/app/consumer/${userId}/orders`);
  }

  getOrderDetails(userId: string, orderId: string): Observable<OrderResponse> {
    return this.http.get<OrderResponse>(`${this.baseUrl}/app/consumer/${userId}/orders/${orderId}`);
  }

  /** Admin: fetch one order fresh (enriched customer + items). */
  getAdminOrder(orderId: string): Observable<OrderResponse> {
    return this.http.get<OrderResponse>(`${this.baseUrl}/admin/orders/${orderId}`);
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
