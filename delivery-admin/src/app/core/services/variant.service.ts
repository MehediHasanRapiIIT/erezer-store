import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** One choice of an option: "Black", with its swatch when the option is a colour. */
export interface ProductOptionValue {
  /** Made by the server; null for a choice that is new. */
  id: string | null;
  value: string;
  hex?: string | null;
}

/** One option of a product ("Colour") with its choices. */
export interface ProductOption {
  /** Made by the server; null for an option that is new. */
  id: string | null;
  name: string;
  /** COLOUR shows a swatch for each choice. */
  kind: 'COLOUR' | 'TEXT';
  values: ProductOptionValue[];
}

/** One choice a variant is made of: "Colour: Black". */
export interface VariantOption {
  optionId: string;
  option: string;
  valueId: string;
  value: string;
  hex?: string | null;
}

export interface VariantRequest {
  /** The combination of the product's own options, option id to value id, for a new variant. */
  options?: Record<string, string> | null;
  size?: string | null;
  /** DROP_SHOULDER or REGULAR_FIT for a product that comes in fits; absent otherwise. */
  fit?: string | null;
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
  /** DROP_SHOULDER or REGULAR_FIT; null when the product has no fits. */
  fit?: string | null;
  fitLabel?: string | null;
  sku: string | null;
  stockQuantity: number | null;
  priceOverride: number | null;
  /** The combination of the product's own options, as a key; null when it has none. */
  optionKey?: string | null;
  options?: VariantOption[];
  /** "Black / Long"; null when the product has no options. */
  optionLabel?: string | null;
}

/** One fit of a product: offered, and its own price when {@link changePrice} is set. */
export interface FitChoiceRequest {
  fit: string;
  price?: number | null;
  /** False leaves the prices of this fit's sizes as they are. */
  changePrice?: boolean;
}

export interface BulkFitsRequest {
  scope: 'PRODUCTS' | 'CATEGORY';
  productIds?: number[];
  categoryId?: number;
  fits: string[];
}

export interface BulkFitsResult {
  changed: number;
  withoutSizes: number;
  message: string;
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

  /** Several sizes at once; all are added or none are. */
  createMany(productId: number, payload: VariantRequest[]): Observable<VariantResponse[]> {
    return this.http.post<VariantResponse[]>(`${this.base}/admin/products/${productId}/variants/bulk`, payload);
  }

  update(productId: number, variantId: number, payload: VariantRequest): Observable<VariantResponse> {
    return this.http.put<VariantResponse>(`${this.base}/admin/products/${productId}/variants/${variantId}`, payload);
  }

  /**
   * Sets the fits a product comes in and returns its sizes as they now stand.
   * A fit being added gets every size with no stock; one being removed goes with
   * its stock; none at all folds the fits' stock back into plain sizes.
   */
  setFits(productId: number, fits: FitChoiceRequest[]): Observable<VariantResponse[]> {
    return this.http.put<VariantResponse[]>(`${this.base}/admin/products/${productId}/fits`, { fits });
  }

  /** The same fits for the chosen products or a whole category; prices are left alone. */
  setFitsForMany(request: BulkFitsRequest): Observable<BulkFitsResult> {
    return this.http.put<BulkFitsResult>(`${this.base}/admin/product-fits/bulk`, request);
  }

  /** The product's own options - colour, and anything else - with their choices. */
  getOptions(productId: number): Observable<ProductOption[]> {
    return this.http.get<ProductOption[]>(`${this.base}/api/products/${productId}/options`);
  }

  /**
   * Sets the options to exactly these and answers with the variants as they now
   * stand. What the product had becomes the first choice of a new option; a new
   * choice is added with no stock; a choice removed goes with its stock.
   * `restoreMissing` also puts back combinations that were deleted.
   */
  setOptions(productId: number, options: ProductOption[], restoreMissing = false): Observable<{ options: ProductOption[]; variants: VariantResponse[] }> {
    return this.http.put<{ options: ProductOption[]; variants: VariantResponse[] }>(
      `${this.base}/admin/products/${productId}/options`, options, { params: { restoreMissing } });
  }

  delete(productId: number, variantId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/products/${productId}/variants/${variantId}`);
  }
}
