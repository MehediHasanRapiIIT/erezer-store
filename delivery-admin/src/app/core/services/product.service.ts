import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { HttpEvent, HttpEventType } from '@angular/common/http';
import { Observable, filter, map, tap } from 'rxjs';
import { VariantRequest } from './variant.service';
import { environment } from '../../../environments/environment';
import { StockDisplay, PageResponse, ProductRequest, ProductResponse } from '../models/api.models';

/** One row of "Add several products". */
export interface BatchItem {
  name: string;
  productCode: string;
  /** Its own price, when it differs from the shared one. */
  price?: number | null;
  /** Its own size chart, when it differs from the shared one; 0 for none of its own. */
  sizeChartId?: number | null;
  pictures: File[];
}

/** What can be done to many products at once. Matches the backend's BulkProductAction. */
export type BulkProductAction =
  | 'MOVE_CATEGORY' | 'DELETE' | 'SHOW' | 'HIDE' | 'FEATURE' | 'UNFEATURE'
  | 'NEW_ARRIVAL_ON' | 'NEW_ARRIVAL_OFF' | 'NEVER_DISCOUNT_ON' | 'NEVER_DISCOUNT_OFF'
  | 'STOCK_SHOW_QUANTITY' | 'STOCK_SHOW_LABELS' | 'SET_SIZE_CHART';

@Injectable({ providedIn: 'root' })
export class ProductService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  getProductsPaged(page: number, size: number): Observable<PageResponse<ProductResponse>> {
    return this.http.get<PageResponse<ProductResponse>>(`${this.baseUrl}/api/products/paged`, {
      params: { page: String(page), size: String(size) },
    });
  }

  /**
   * One page of the Products page: the server searches name, SKU, brand and
   * category name across all products, optionally within one category.
   */
  searchAdmin(query: { q?: string; categoryId?: number | null; page: number; size: number }):
      Observable<PageResponse<ProductResponse>> {
    const params: Record<string, string> = { page: String(query.page), size: String(query.size) };
    if (query.q) params['q'] = query.q;
    if (query.categoryId != null) params['categoryId'] = String(query.categoryId);
    return this.http.get<PageResponse<ProductResponse>>(`${this.baseUrl}/admin/products`, { params });
  }

  /**
   * One page of the public shop search (name, brand, description). For
   * pickers that shouldn't download every product; needs no staff permission.
   */
  browse(q: string, page: number, size: number, categoryId?: number | null): Observable<PageResponse<ProductResponse>> {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (q) params['q'] = q;
    if (categoryId != null) params['categoryId'] = String(categoryId);
    return this.http.get<PageResponse<ProductResponse>>(`${this.baseUrl}/api/products/browse`, { params });
  }

  getProduct(id: number): Observable<ProductResponse> {
    return this.http.get<ProductResponse>(`${this.baseUrl}/api/products/${id}`);
  }

  createProduct(dto: ProductRequest, image?: File): Observable<ProductResponse> {
    const formData = new FormData();
    formData.append(
      'productRequestDTO',
      new Blob([JSON.stringify(dto)], { type: 'application/json' })
    );
    if (image) {
      formData.append('image', image);
    }
    return this.http.post<ProductResponse>(`${this.baseUrl}/api/products`, formData);
  }

  /**
   * "Add product" in one click: the product, its pictures (the first is the main
   * one) and its sizes. The server saves all of it or none of it.
   */
  createWithEverything(dto: ProductRequest, sizes: VariantRequest[], pictures: File[],
                       onProgress?: (percent: number) => void): Observable<ProductResponse> {
    const form = new FormData();
    form.append('product', new Blob([JSON.stringify(dto)], { type: 'application/json' }));
    if (sizes.length > 0) {
      form.append('sizes', new Blob([JSON.stringify(sizes)], { type: 'application/json' }));
    }
    for (const picture of pictures) {
      form.append('pictures', picture, picture.name);
    }
    return this.withProgress<ProductResponse>(`${this.baseUrl}/admin/products/full`, form, onProgress);
  }

  /** The next product codes for a category, e.g. ["EP-1003", "EP-1004"]. A suggestion only. */
  nextCodes(categoryId: number, count = 1): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/admin/products/next-codes`, {
      params: { categoryId: String(categoryId), count: String(count) },
    });
  }

  /**
   * "Add several products": rows sharing one description, category, price,
   * discount and sizes, each with its own name, code, pictures and price.
   * Saved all together or not at all.
   */
  createBatch(shared: ProductRequest, sizes: VariantRequest[], items: BatchItem[],
              onProgress?: (percent: number) => void): Observable<ProductResponse[]> {
    const form = new FormData();
    const batch = {
      shared,
      sizes,
      items: items.map((i) => ({ name: i.name, productCode: i.productCode, price: i.price ?? null, sizeChartId: i.sizeChartId ?? null })),
    };
    form.append('batch', new Blob([JSON.stringify(batch)], { type: 'application/json' }));
    items.forEach((item, row) => {
      for (const picture of item.pictures) form.append(`pictures-${row}`, picture, picture.name);
    });
    return this.withProgress<ProductResponse[]>(`${this.baseUrl}/admin/products/batch`, form, onProgress);
  }

  /**
   * Posts a form and reports how much of it has gone up. Photos are sent at
   * their original size, so a whole shoot can take minutes on a shop's
   * connection, and "Saving…" alone would look stuck.
   */
  private withProgress<T>(url: string, form: FormData, onProgress?: (percent: number) => void): Observable<T> {
    return this.http.post<T>(url, form, { reportProgress: true, observe: 'events' }).pipe(
      tap((event: HttpEvent<T>) => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          onProgress?.(Math.round((event.loaded / event.total) * 100));
        }
      }),
      filter((event: HttpEvent<T>) => event.type === HttpEventType.Response),
      map((event) => (event as { body: T }).body),
    );
  }

  updateProduct(id: number, dto: ProductRequest, image?: File): Observable<ProductResponse> {
    const formData = new FormData();
    // PUT endpoint uses @RequestParam flat fields, not @RequestPart JSON blob
    formData.append('categoryId', String(dto.categoryId));
    formData.append('name', dto.name);
    formData.append('productCode', dto.productCode);
    formData.append('description', dto.description);
    formData.append('price', String(dto.price));
    if (dto.discountPercentage != null) {
      formData.append('discountPercentage', String(dto.discountPercentage));
    }
    if (dto.discountAmount != null) {
      formData.append('discountAmount', String(dto.discountAmount));
    }
    formData.append('shopId', String(dto.shopId));
    formData.append('isAvailable', String(dto.isAvailable));
    if (dto.isNewArrival != null)      formData.append('isNewArrival', String(dto.isNewArrival));
    if (dto.isFeatured != null)        formData.append('isFeatured', String(dto.isFeatured));
    if (dto.unit != null)              formData.append('unit', dto.unit);
    if (dto.lowStockThreshold != null) formData.append('lowStockThreshold', String(dto.lowStockThreshold));
    if (dto.brand != null)             formData.append('brand', dto.brand);
    if (dto.gender != null)            formData.append('gender', dto.gender);
    if (dto.material != null)          formData.append('material', dto.material);
    if (dto.careInstructions != null)  formData.append('careInstructions', dto.careInstructions);
    if (dto.customSizeEnabled != null)   formData.append('customSizeEnabled', String(dto.customSizeEnabled));
    if (dto.customSizeSurcharge != null) formData.append('customSizeSurcharge', String(dto.customSizeSurcharge));
    if (dto.customSizeNote != null)      formData.append('customSizeNote', dto.customSizeNote);
    if (dto.discountExcluded != null)    formData.append('discountExcluded', String(dto.discountExcluded));
    if (dto.stockDisplay != null)        formData.append('stockDisplay', dto.stockDisplay);
    // 0 takes the product's own chart away; leaving it out would keep the old one.
    if (dto.sizeChartId != null)           formData.append('sizeChartId', String(dto.sizeChartId));
    if (dto.regularFitSizeChartId != null) formData.append('regularFitSizeChartId', String(dto.regularFitSizeChartId));
    if (image) {
      formData.append('image', image);
    }
    return this.http.put<ProductResponse>(`${this.baseUrl}/api/products/${id}`, formData);
  }

  /** Toggle the "Featured products" home flag only (doesn't touch price/stock). */
  setFeatured(id: number, value: boolean): Observable<ProductResponse> {
    return this.http.patch<ProductResponse>(
      `${this.baseUrl}/admin/products/${id}/featured`, null, { params: { value } });
  }

  /** The products list's "Show qty" switch: quantity or labels on this product's page. */
  setStockDisplay(id: number, value: StockDisplay): Observable<ProductResponse> {
    return this.http.patch<ProductResponse>(
      `${this.baseUrl}/admin/products/${id}/stock-display`, null, { params: { value } });
  }

  /**
   * One action for the products ticked on the Products page. The answer is a
   * sentence saying what was done, ready to show.
   */
  bulk(action: BulkProductAction, productIds: number[], categoryId?: number | null, sizeChartId?: number | null): Observable<{ changed: number; message: string }> {
    return this.http.put<{ changed: number; message: string }>(
      `${this.baseUrl}/admin/products/bulk`, { action, productIds, categoryId: categoryId ?? null, sizeChartId: sizeChartId ?? null });
  }

  deleteProduct(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/products/${id}`);
  }
}
