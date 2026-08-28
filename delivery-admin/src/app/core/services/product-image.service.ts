import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ProductImageResponse {
  id: number;
  productId: number;
  url: string;
  altText: string | null;
  sortOrder: number;
  isPrimary: boolean;
}

export interface ProductImageMetadata {
  altText?: string | null;
  sortOrder?: number;
  isPrimary?: boolean;
}

@Injectable({ providedIn: 'root' })
export class ProductImageService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  list(productId: number): Observable<ProductImageResponse[]> {
    return this.http.get<ProductImageResponse[]>(`${this.base}/admin/products/${productId}/images`);
  }

  upload(
    productId: number,
    file: File,
    options: { altText?: string; sortOrder?: number; isPrimary?: boolean } = {},
  ): Observable<ProductImageResponse> {
    const form = new FormData();
    form.append('file', file);
    if (options.altText)  form.append('altText', options.altText);
    if (options.sortOrder != null) form.append('sortOrder', String(options.sortOrder));
    if (options.isPrimary != null) form.append('isPrimary', String(options.isPrimary));
    return this.http.post<ProductImageResponse>(`${this.base}/admin/products/${productId}/images`, form);
  }

  updateMetadata(
    productId: number,
    imageId: number,
    metadata: ProductImageMetadata,
  ): Observable<ProductImageResponse> {
    return this.http.put<ProductImageResponse>(
      `${this.base}/admin/products/${productId}/images/${imageId}`,
      metadata,
    );
  }

  delete(productId: number, imageId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/products/${productId}/images/${imageId}`);
  }
}
