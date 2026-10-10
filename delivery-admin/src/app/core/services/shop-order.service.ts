import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** One ranked product: its place in the shop and enough to recognise it. */
export interface ShopOrderItem {
  id: number;
  /** 1 is first. */
  position: number;
  name: string;
  productCode: string | null;
  sku: string | null;
  imageUrl: string | null;
  categoryName: string | null;
  price: number | null;
  stockQuantity: number;
  /** False when the product is hidden from the shop. */
  available: boolean;
}

/**
 * The order products appear in on the Shop page and the category pages. The
 * whole order is saved at once, as the product ids from first to last.
 */
@Injectable({ providedIn: 'root' })
export class ShopOrderService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  list(): Observable<ShopOrderItem[]> {
    return this.http.get<ShopOrderItem[]>(`${this.baseUrl}/admin/shop-order`);
  }

  save(productIds: number[]): Observable<ShopOrderItem[]> {
    return this.http.put<ShopOrderItem[]>(`${this.baseUrl}/admin/shop-order`, { productIds });
  }
}
