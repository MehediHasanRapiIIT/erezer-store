import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type ReturnStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'PICKED_UP' | 'REFUNDED';

export interface ReturnItem {
  id: string;
  orderItemId: string;
  productId: number | null;
  productName: string | null;
  quantity: number;
  condition: string | null;
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
  reason: string;
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

export interface ReturnDecisionPayload {
  adminNotes?: string;
  refundAmount?: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class ReturnService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  list(status?: string, page = 0, size = 20): Observable<PageResponse<ReturnRequestResponse>> {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (status && status !== 'ALL') params['status'] = status;
    return this.http.get<PageResponse<ReturnRequestResponse>>(`${this.base}/admin/returns`, { params });
  }

  get(id: string): Observable<ReturnRequestResponse> {
    return this.http.get<ReturnRequestResponse>(`${this.base}/admin/returns/${id}`);
  }

  approve(id: string, payload: ReturnDecisionPayload = {}): Observable<ReturnRequestResponse> {
    return this.http.post<ReturnRequestResponse>(`${this.base}/admin/returns/${id}/approve`, payload);
  }

  reject(id: string, payload: ReturnDecisionPayload = {}): Observable<ReturnRequestResponse> {
    return this.http.post<ReturnRequestResponse>(`${this.base}/admin/returns/${id}/reject`, payload);
  }

  markPickedUp(id: string): Observable<ReturnRequestResponse> {
    return this.http.post<ReturnRequestResponse>(`${this.base}/admin/returns/${id}/picked-up`, {});
  }

  refund(id: string, payload: ReturnDecisionPayload = {}): Observable<ReturnRequestResponse> {
    return this.http.post<ReturnRequestResponse>(`${this.base}/admin/returns/${id}/refund`, payload);
  }
}
