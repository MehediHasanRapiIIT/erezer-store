import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type CustomOrderStatus = 'NEW' | 'IN_REVIEW' | 'QUOTED' | 'CONFIRMED' | 'DELIVERED' | 'CLOSED';

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface CustomOrderImage {
  view: string;
  url: string;
}

export interface CustomOrderSummary {
  id: string;
  reference: string;
  customerName: string;
  email: string;
  phone: string;
  itemName: string | null;
  status: CustomOrderStatus;
  thumbnailUrl: string | null;
  createdAt: string;
}

export interface CustomOrderDetail {
  id: string;
  reference: string;
  firstName: string;
  lastName: string;
  phone: string;
  email: string;
  shippingAddress: string;
  apartment: string | null;
  city: string;
  zipCode: string | null;
  country: string;
  notes: string;
  itemName: string | null;
  colorName: string | null;
  size: string | null;
  printTechnique: string | null;
  designJson: string | null;
  status: CustomOrderStatus;
  adminNotes: string | null;
  images: CustomOrderImage[];
  createdAt: string;
}

/** Admin inbox for custom-design "Submit for Price" quote requests. */
@Injectable({ providedIn: 'root' })
export class CustomOrderService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  list(status?: string, page = 0, size = 30, history = false): Observable<PageResponse<CustomOrderSummary>> {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (history) params['history'] = 'true';
    if (status && status !== 'ALL') params['status'] = status;
    return this.http.get<PageResponse<CustomOrderSummary>>(
      `${this.base}/admin/custom-orders`, { params });
  }

  get(id: string): Observable<CustomOrderDetail> {
    return this.http.get<CustomOrderDetail>(`${this.base}/admin/custom-orders/${id}`);
  }

  updateStatus(id: string, status: CustomOrderStatus, adminNotes?: string): Observable<CustomOrderDetail> {
    return this.http.patch<CustomOrderDetail>(
      `${this.base}/admin/custom-orders/${id}`, { status, adminNotes });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/custom-orders/${id}`);
  }
}
