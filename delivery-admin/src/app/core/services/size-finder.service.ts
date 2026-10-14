import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** One size of a size finder table: the tallest height (inches) and heaviest weight (kg) it suits; null for "anything above". */
export interface FinderRule {
  size: string;
  heightUpToIn: number | null;
  weightUpToKg: number | null;
}

export interface FinderTable {
  enabled: boolean;
  rules: FinderRule[];
}

/** The size finder: the shop-wide switch, the table for products with no fit, and one table per fit. */
export interface SizeFinder {
  enabled: boolean;
  general: FinderTable;
  fits: Record<string, FinderTable>;
}

/** The size finder's rules ("Don't know your size?"), on the Size Charts page. */
@Injectable({ providedIn: 'root' })
export class SizeFinderService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  get(): Observable<SizeFinder> {
    return this.http.get<SizeFinder>(`${this.baseUrl}/api/size-finder`);
  }

  setShop(enabled: boolean): Observable<SizeFinder> {
    return this.http.put<SizeFinder>(`${this.baseUrl}/admin/size-finder/shop`, { enabled });
  }

  /** scope: GENERAL or a fit's code. Rules null: only the switch; an empty list: back to the standard rules. */
  save(scope: string, enabled: boolean | null, rules: FinderRule[] | null): Observable<SizeFinder> {
    return this.http.put<SizeFinder>(`${this.baseUrl}/admin/size-finder/${scope}`, { enabled, rules });
  }
}
