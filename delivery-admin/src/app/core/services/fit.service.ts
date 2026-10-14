import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, firstValueFrom, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { setFits } from '../../features/products/shared/fit-sizes.component';

/** One fit in the shop's list (Drop Shoulder, Regular Fit, Oversized…). */
export interface ShopFit {
  id: number;
  /** As a size stores it: DROP_SHOULDER. Never changes. */
  code: string;
  name: string;
  skuCode: string;
  /** The library chart this fit's sizes show; null to follow the product's chart. */
  sizeChartId: number | null;
  sizeChartName: string | null;
  position: number;
  /** Hidden: kept for products that have it, not offered for new ones. */
  hidden: boolean;
  /** How many products have a size in this fit (admin list only). */
  productCount: number;
}

/**
 * The shop's fits. The panel reads them once when it starts (so every product
 * form, the Fits button and Inventory offer the same list), and again after
 * every change on the Size Charts page.
 */
@Injectable({ providedIn: 'root' })
export class FitService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  /** Reads the list into the panel. Keeps the built-in two if it cannot be read. */
  load(): Promise<void> {
    return firstValueFrom(this.http.get<ShopFit[]>(`${this.baseUrl}/api/fits`))
      .then((fits) => {
        if (fits?.length) setFits(fits.map(toInfo));
      })
      .catch(() => undefined);
  }

  /** Every fit with how many products use it, for the Size Charts page. */
  adminList(): Observable<ShopFit[]> {
    return this.http.get<ShopFit[]>(`${this.baseUrl}/admin/fits`).pipe(tap((fits) => setFits(fits.map(toInfo))));
  }

  create(name: string, sizeChartId: number | null): Observable<ShopFit> {
    return this.http.post<ShopFit>(`${this.baseUrl}/admin/fits`, { name, sizeChartId: sizeChartId ?? 0 });
  }

  /** A missing name or hidden flag is left as it is; a chart of 0 takes the fit's chart away. */
  update(id: number, change: { name?: string; sizeChartId?: number | null; hidden?: boolean }): Observable<ShopFit> {
    return this.http.put<ShopFit>(`${this.baseUrl}/admin/fits/${id}`, {
      name: change.name ?? null,
      sizeChartId: change.sizeChartId === undefined ? null : (change.sizeChartId ?? 0),
      hidden: change.hidden ?? null,
    });
  }

  reorder(ids: number[]): Observable<ShopFit[]> {
    return this.http.put<ShopFit[]>(`${this.baseUrl}/admin/fits/order`, { fitIds: ids });
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/admin/fits/${id}`);
  }
}

function toInfo(f: ShopFit) {
  return { value: f.code, label: f.name, hidden: f.hidden, sizeChartId: f.sizeChartId, sizeChartName: f.sizeChartName };
}
