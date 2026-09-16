import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../models/api.models';

export type CouponDiscountType = 'PERCENT' | 'FLAT' | 'FREE_SHIPPING';

export interface CouponRequest {
  code: string;
  discountType: CouponDiscountType;
  discountValue?: number | null;
  minOrderAmount?: number | null;
  usageLimit?: number | null;
  perUserLimit?: number | null;
  validFrom?: string | null;
  validTo?: string | null;
  isActive?: boolean | null;
  description?: string | null;
}

export interface CouponResponse {
  id: string;
  code: string;
  discountType: CouponDiscountType;
  discountValue: number | null;
  minOrderAmount: number | null;
  usageLimit: number | null;
  perUserLimit: number | null;
  timesUsed: number;
  validFrom: string | null;
  validTo: string | null;
  isActive: boolean;
  description: string | null;
}

/** The shop-wide promo code switch. Null means on. */
export interface CouponSwitch {
  couponsEnabled: boolean | null;
}

@Injectable({ providedIn: 'root' })
export class CouponService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  /** One page of coupons, newest first; `q` searches the code and description. */
  list(page = 0, size = 20, q?: string): Observable<PageResponse<CouponResponse>> {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (q?.trim()) params['q'] = q.trim();
    return this.http.get<PageResponse<CouponResponse>>(`${this.base}/admin/coupons`, { params });
  }

  create(payload: CouponRequest): Observable<CouponResponse> {
    return this.http.post<CouponResponse>(`${this.base}/admin/coupons`, payload);
  }

  update(id: string, payload: CouponRequest): Observable<CouponResponse> {
    return this.http.put<CouponResponse>(`${this.base}/admin/coupons/${id}`, payload);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/coupons/${id}`);
  }

  getSwitch(): Observable<CouponSwitch> {
    return this.http.get<CouponSwitch>(`${this.base}/admin/coupons/switch`);
  }

  updateSwitch(change: CouponSwitch): Observable<CouponSwitch> {
    return this.http.put<CouponSwitch>(`${this.base}/admin/coupons/switch`, change);
  }
}
