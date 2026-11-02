import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** The Settings page's "Showcase screen" section: the in-store screen at /showcase. */
export interface ShowcaseSettings {
  enabled: boolean;
  slideSeconds: number;
  /** NEW: new arrivals first; ALL: every product; PICKED: only productIds, in that order. */
  productMode: 'NEW' | 'ALL' | 'PICKED';
  productIds: number[];
  showQr: boolean;
}

@Injectable({ providedIn: 'root' })
export class ShowcaseService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  get(): Observable<ShowcaseSettings> {
    return this.http.get<ShowcaseSettings>(`${this.baseUrl}/admin/showcase`);
  }

  save(settings: ShowcaseSettings): Observable<ShowcaseSettings> {
    return this.http.put<ShowcaseSettings>(`${this.baseUrl}/admin/showcase`, settings);
  }
}
