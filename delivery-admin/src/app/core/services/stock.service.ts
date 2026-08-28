import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BulkStockUpdateRequest, InventorySummary, StockResponse, StockUpdateRequest } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class StockService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  getSummary(): Observable<InventorySummary> {
    return this.http.get<InventorySummary>(`${this.baseUrl}/admin/inventory/summary`);
  }

  getAllStock(): Observable<StockResponse[]> {
    return this.http.get<StockResponse[]>(`${this.baseUrl}/admin/inventory`);
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
