import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse, ProductResponse } from '../models/api.models';

/** Admin create/update payload for a bundle offer. */
/** The kinds of bundle offer. Matches the backend's BundleType. */
export type BundleType = 'FIXED_PRICE' | 'BUY_X_GET_Y' | 'QUANTITY_DISCOUNT';

/** One step of a quantity discount: this many items or more get this percentage off. */
export interface BundleTier {
  quantity: number | null;
  percentOff: number | null;
}

export interface BundleRequest {
  name: string;
  label?: string | null;
  description?: string | null;
  offerType: BundleType;
  /** The steps of a quantity discount; empty for the other kinds. */
  tiers: BundleTier[];
  /** Not sent for a quantity discount, which has no set number of items and no one price. */
  buyCount: number | null;
  getCount: number | null;
  bundlePrice: number | null;
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
  offerType: BundleType;
  /** The offer in words: "Any 3 for ৳999", "Buy 2 Get 1 Free", "Buy 2, save 10% · Buy 3, save 15%". */
  headline: string;
  tiers: BundleTier[];
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
