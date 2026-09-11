import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** What happens to each product's price. */
export type PriceMode = 'KEEP' | 'SET' | 'RAISE_AMOUNT' | 'LOWER_AMOUNT' | 'RAISE_PERCENT' | 'LOWER_PERCENT';
/** What happens to each product's sale discount. */
export type SaleMode = 'KEEP' | 'SET' | 'REMOVE';

/** `priceValue` is taka or a percentage, depending on `priceMode`. */
export interface PriceChangeRequest {
  categoryId: number;
  priceMode: PriceMode;
  priceValue: number | null;
  saleMode: SaleMode;
  salePercent: number | null;
  /** The ticked products; needed to apply. */
  productIds?: number[];
}

export interface PriceChangeSize {
  variantId: number;
  size: string;
  oldPrice: number;
  newPrice: number;
}

/** One product. Sale prices are null when it isn't on sale; `problem` says why it can't be changed. */
export interface PriceChangeRow {
  productId: number;
  name: string;
  sku: string | null;
  productCode: string | null;
  imageUrl: string | null;
  oldPrice: number | null;
  newPrice: number | null;
  oldSalePrice: number | null;
  newSalePrice: number | null;
  sizes: PriceChangeSize[];
  changed: boolean;
  problem: string | null;
}

/**
 * The counts and `changeableIds` cover the whole category, whatever the page
 * or search; `rows` is one page of the products matching the search.
 */
export interface PriceChangePreview {
  categoryId: number;
  categoryName: string;
  productCount: number;
  changedCount: number;
  problemCount: number;
  /** Every product that would change and can be changed: ticked unless the person unticks it. */
  changeableIds: number[];
  page: number;
  size: number;
  /** Products matching the search, over all pages. */
  totalRows: number;
  totalPages: number;
  rows: PriceChangeRow[];
}

/** Changing prices across a category (CATEGORY-PRICE-PLAN.md). */
@Injectable({ providedIn: 'root' })
export class PriceChangeService {
  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/admin/products/price-change`;

  /** One page of what the change would do; `q` searches product name and SKU. Saves nothing. */
  preview(r: PriceChangeRequest, q: string, page: number, size: number): Observable<PriceChangePreview> {
    const params: Record<string, string> = {
      categoryId: String(r.categoryId), priceMode: r.priceMode, saleMode: r.saleMode,
      page: String(page), size: String(size),
    };
    if (r.priceValue != null) params['priceValue'] = String(r.priceValue);
    if (r.salePercent != null) params['salePercent'] = String(r.salePercent);
    if (q) params['q'] = q;
    return this.http.get<PriceChangePreview>(`${this.base}/preview`, { params });
  }

  /** Applies the change to the ticked products, all together or not at all. */
  apply(r: PriceChangeRequest): Observable<PriceChangePreview> {
    return this.http.post<PriceChangePreview>(this.base, r);
  }
}
