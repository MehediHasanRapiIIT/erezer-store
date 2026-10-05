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

/**
 * One picture a custom order was made from, at its original size: the file the
 * customer uploaded, or a logo from the shop's library. This is what to print
 * from; the order's `images` are only screen-size previews of the garment.
 */
export interface CustomOrderSourceFile {
  /** Which side of the garment it is on. */
  view: string;
  /** Where the original file is; for EDITED, the picture itself as a data: address. */
  url: string;
  /** The customer's own file, a shop logo, or a picture changed in the studio (background removed). */
  kind: 'CUSTOMER' | 'SHOP' | 'EDITED';
  /** The logo's name in the shop's library; null for a customer's file. */
  name: string | null;
  /** The picture's own size in pixels, when the design recorded it. */
  width: number | null;
  height: number | null;
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
  /** The original files the design was made from, for printing. */
  sourceFiles?: CustomOrderSourceFile[] | null;
  createdAt: string;
}

/** Admin inbox for custom-design "Submit for Price" quote requests. */
@Injectable({ providedIn: 'root' })
export class CustomOrderService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  /** One page of requests; the server searches reference, customer name, phone, email and item. */
  list(status?: string, page = 0, size = 30, history = false, q = ''): Observable<PageResponse<CustomOrderSummary>> {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (history) params['history'] = 'true';
    if (status && status !== 'ALL') params['status'] = status;
    if (q) params['q'] = q;
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
