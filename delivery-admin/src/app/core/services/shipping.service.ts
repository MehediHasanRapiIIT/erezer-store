import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ShippingZone {
  id: number;
  code: string;
  displayName: string;
  countryCode: string | null;
  flatFee: number;
  isDefault: boolean | null;
  isActive: boolean | null;
}

/** The admin Shipping page: zone prices and the free-shipping rules. */
export interface ShippingSettings {
  zones: ShippingZone[];
  /** No order pays shipping. */
  freeAll: boolean;
  /** Orders from offerMin ship free. */
  offerEnabled: boolean;
  offerMin: number | null;
}

/** A change to the free-shipping rules; fields left out stay as they are. */
export interface ShippingRulesChange {
  freeAll?: boolean;
  offerEnabled?: boolean;
  offerMin?: number;
}

/**
 * One delivery-charge change, made from the Products page.
 *
 * Chosen products and the whole shop set the charge on the products themselves;
 * a category sets it on the category, so products added to it later are covered.
 */
export interface ShippingChargeChange {
  scope: 'PRODUCTS' | 'CATEGORY' | 'ALL';
  productIds?: number[];
  categoryId?: number;
  /** In taka; 0 means delivered free. Left out when useAreaPrice is true. */
  charge?: number;
  /** True to take the charge away, so the customer's area price decides again. */
  useAreaPrice?: boolean;
}

/** What a delivery-charge change did, for the message the admin sees. */
export interface ShippingChargeResult {
  products: number;
  categories: number;
  /** Products in a category that keep a charge of their own. */
  keptOwnCharge: number;
  scopeLabel: string;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class ShippingService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  get(): Observable<ShippingSettings> {
    return this.http.get<ShippingSettings>(`${this.base}/admin/shipping`);
  }

  updateZoneFee(zoneId: number, flatFee: number): Observable<ShippingSettings> {
    return this.http.put<ShippingSettings>(`${this.base}/admin/shipping/zones/${zoneId}`, { flatFee });
  }

  updateRules(change: ShippingRulesChange): Observable<ShippingSettings> {
    return this.http.put<ShippingSettings>(`${this.base}/admin/shipping/rules`, change);
  }

  /** Sets, or takes away, the delivery charge on products or a category. */
  setCharges(change: ShippingChargeChange): Observable<ShippingChargeResult> {
    return this.http.put<ShippingChargeResult>(`${this.base}/admin/shipping/charges`, change);
  }
}
