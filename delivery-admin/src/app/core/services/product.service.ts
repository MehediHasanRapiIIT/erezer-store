import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse, ProductRequest, ProductResponse } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class ProductService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  getProducts(): Observable<ProductResponse[]> {
    return this.http.get<ProductResponse[]>(`${this.baseUrl}/api/products`);
  }

  getProductsPaged(page: number, size: number): Observable<PageResponse<ProductResponse>> {
    return this.http.get<PageResponse<ProductResponse>>(`${this.baseUrl}/api/products/paged`, {
      params: { page: String(page), size: String(size) },
    });
  }

  searchProducts(name: string): Observable<ProductResponse[]> {
    return this.http.get<ProductResponse[]>(`${this.baseUrl}/api/products/search`, {
      params: { name },
    });
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

  deleteProduct(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/products/${id}`);
  }
}
