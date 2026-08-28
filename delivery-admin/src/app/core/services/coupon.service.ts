import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

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

@Injectable({ providedIn: 'root' })
export class CouponService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  list(): Observable<CouponResponse[]> {
    return this.http.get<CouponResponse[]>(`${this.base}/admin/coupons`);
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
}
