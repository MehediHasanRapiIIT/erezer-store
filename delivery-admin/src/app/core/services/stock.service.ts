import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  BulkStockUpdateRequest, InventorySummary, PageResponse, StockResponse, StockUpdateRequest,
} from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class StockService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  getSummary(): Observable<InventorySummary> {
    return this.http.get<InventorySummary>(`${this.baseUrl}/admin/inventory/summary`);
  }

  /** One page of stock rows; the server searches product name and SKU across every product. */
  getStockPage(q: string, page: number, size: number): Observable<PageResponse<StockResponse>> {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (q) params['q'] = q;
    return this.http.get<PageResponse<StockResponse>>(`${this.baseUrl}/admin/inventory`, { params });
  }

  /** One page of products low on stock or out of stock, for the restock alerts. */
  getLowStock(page: number, size: number, q = ''): Observable<PageResponse<StockResponse>> {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (q) params['q'] = q;
    return this.http.get<PageResponse<StockResponse>>(`${this.baseUrl}/admin/inventory/alerts`, { params });
  }

  getStock(productId: number): Observable<StockResponse> {
    return this.http.get<StockResponse>(`${this.baseUrl}/admin/products/${productId}/stock`);
  }

  updateStock(productId: number, request: StockUpdateRequest): Observable<StockResponse> {
    return this.http.put<StockResponse>(`${this.baseUrl}/admin/products/${productId}/stock`, request);
  }

  bulkUpdateStock(request: BulkStockUpdateRequest): Observable<StockResponse[]> {
    return this.http.put<StockResponse[]>(`${this.baseUrl}/admin/inventory/bulk`, request);
  }
}
