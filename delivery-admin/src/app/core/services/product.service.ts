import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { StockDisplay, PageResponse, ProductRequest, ProductResponse } from '../models/api.models';

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

  deleteProduct(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/products/${id}`);
  }
}
