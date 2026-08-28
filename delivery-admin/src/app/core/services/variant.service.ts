import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface VariantRequest {
  size?: string | null;
  sku?: string | null;
  stockQuantity?: number | null;
  priceOverride?: number | null;
  name?: string | null;
}

export interface VariantResponse {
  id: number;
  productId: number;
  name: string | null;
  size: string | null;
  sku: string | null;
  stockQuantity: number | null;
  priceOverride: number | null;
}

@Injectable({ providedIn: 'root' })
export class VariantService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  list(productId: number): Observable<VariantResponse[]> {
    return this.http.get<VariantResponse[]>(`${this.base}/admin/products/${productId}/variants`);
  }

  create(productId: number, payload: VariantRequest): Observable<VariantResponse> {
    return this.http.post<VariantResponse>(`${this.base}/admin/products/${productId}/variants`, payload);
  }

  update(productId: number, variantId: number, payload: VariantRequest): Observable<VariantResponse> {
    return this.http.put<VariantResponse>(`${this.base}/admin/products/${productId}/variants/${variantId}`, payload);
  }

  delete(productId: number, variantId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/products/${productId}/variants/${variantId}`);
  }
}
