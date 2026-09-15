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
}
