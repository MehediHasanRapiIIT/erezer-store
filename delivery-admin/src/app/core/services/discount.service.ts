import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type DiscountScope = 'PRODUCT' | 'CATEGORY' | 'GLOBAL';
export type DiscountType = 'PERCENT' | 'FLAT';

export interface DiscountRequest {
  name: string;
  scope: DiscountScope;
  discountType: DiscountType;
  discountValue?: number | null;
  targetId?: number | null;
  stackable?: boolean | null;
  priority?: number | null;
  validFrom?: string | null;
  validTo?: string | null;
  isActive?: boolean | null;
  description?: string | null;
}

export interface DiscountResponse {
  id: string;
  name: string;
  scope: DiscountScope;
  discountType: DiscountType;
  discountValue: number | null;
  targetId: number | null;
  stackable: boolean;
  priority: number;
  validFrom: string | null;
  validTo: string | null;
  isActive: boolean;
  description: string | null;
}

@Injectable({ providedIn: 'root' })
export class DiscountService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  list(): Observable<DiscountResponse[]> {
    return this.http.get<DiscountResponse[]>(`${this.base}/admin/discounts`);
  }

  create(payload: DiscountRequest): Observable<DiscountResponse> {
    return this.http.post<DiscountResponse>(`${this.base}/admin/discounts`, payload);
  }

  update(id: string, payload: DiscountRequest): Observable<DiscountResponse> {
    return this.http.put<DiscountResponse>(`${this.base}/admin/discounts/${id}`, payload);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/discounts/${id}`);
  }
}
