import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse, ProductResponse } from '../models/api.models';

export type FlashSaleDiscountType = 'PERCENT' | 'FLAT';

/** Payload sent to the admin create/update endpoints. */
export interface FlashSaleRequest {
  name: string;
  label?: string | null;
  discountType: FlashSaleDiscountType;
  discountValue: number;
  startsAt?: string | null;
  endsAt: string;
  couponCode?: string | null;
  minSpend?: number | null;
  isActive: boolean;
  featured?: boolean | null;
  productIds: number[];
}

/** Admin-facing flash sale, including the resolved product list. */
export interface FlashSaleResponse {
  id: string;
  name: string;
  label: string | null;
  discountType: FlashSaleDiscountType;
  discountValue: number;
  startsAt: string | null;
  endsAt: string;
  couponCode: string | null;
  minSpend: number | null;
  isActive: boolean;
  featured: boolean;
  products: ProductResponse[];
}

/**
 * Admin CRUD for flash-sale campaigns. The storefront reads the single active
 * campaign from the public `GET /api/flash-sale`; these endpoints manage them.
 * Mirrors {@link DiscountService}.
 */
@Injectable({ providedIn: 'root' })
export class FlashSaleService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  /** One page from the server; `q` searches on the server. */
  list(page = 0, size = 20, q?: string): Observable<PageResponse<FlashSaleResponse>> {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (q?.trim()) params['q'] = q.trim();
    return this.http.get<PageResponse<FlashSaleResponse>>(`${this.base}/admin/flash-sales`, { params });
  }

  create(payload: FlashSaleRequest): Observable<FlashSaleResponse> {
    return this.http.post<FlashSaleResponse>(`${this.base}/admin/flash-sales`, payload);
  }

  update(id: string, payload: FlashSaleRequest): Observable<FlashSaleResponse> {
    return this.http.put<FlashSaleResponse>(`${this.base}/admin/flash-sales/${id}`, payload);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/flash-sales/${id}`);
  }
}
