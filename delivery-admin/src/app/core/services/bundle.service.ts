import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse, ProductResponse } from '../models/api.models';

/** Admin create/update payload for a bundle offer. */
export interface BundleRequest {
  name: string;
  label?: string | null;
  description?: string | null;
  buyCount: number;
  getCount: number;
  bundlePrice: number;
  compareAtPrice?: number | null;
  isActive: boolean;
  featured?: boolean | null;
  sortOrder?: number | null;
  imageUrls: string[];
  productIds: number[];
}

/** Admin-facing bundle offer, with resolved products. */
export interface BundleResponse {
  id: string;
  name: string;
  label: string | null;
  description: string | null;
  buyCount: number;
  getCount: number;
  slots: number;
  bundlePrice: number;
  compareAtPrice: number | null;
  savings: number | null;
  isActive: boolean;
  featured: boolean;
  sortOrder: number;
  images: string[];
  products: ProductResponse[];
}

/**
 * Admin CRUD for bundle offers ("Buy X Get Y"). The storefront reads active
 * bundles from the public `GET /api/bundles`; these endpoints manage them.
 */
@Injectable({ providedIn: 'root' })
export class BundleService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  /** One page from the server; `q` searches on the server. */
  list(page = 0, size = 20, q?: string): Observable<PageResponse<BundleResponse>> {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (q?.trim()) params['q'] = q.trim();
    return this.http.get<PageResponse<BundleResponse>>(`${this.base}/admin/bundles`, { params });
  }

  create(payload: BundleRequest): Observable<BundleResponse> {
    return this.http.post<BundleResponse>(`${this.base}/admin/bundles`, payload);
  }

  update(id: string, payload: BundleRequest): Observable<BundleResponse> {
    return this.http.put<BundleResponse>(`${this.base}/admin/bundles/${id}`, payload);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/bundles/${id}`);
  }
}
