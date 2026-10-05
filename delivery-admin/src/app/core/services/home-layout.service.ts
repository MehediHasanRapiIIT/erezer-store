import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** One section of the shop's home page: which, and whether it is shown. */
export interface HomeLayoutSection {
  /** The backend's name for the section, e.g. NEW_ARRIVALS. */
  key: string;
  enabled: boolean;
}

/**
 * The shop's home page layout: which sections below the top banner are shown,
 * and in what order. The list always names every section once, top to bottom.
 */
@Injectable({ providedIn: 'root' })
export class HomeLayoutService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  get(): Observable<HomeLayoutSection[]> {
    return this.http.get<HomeLayoutSection[]>(`${this.base}/admin/home-layout`);
  }

  save(layout: HomeLayoutSection[]): Observable<HomeLayoutSection[]> {
    return this.http.put<HomeLayoutSection[]>(`${this.base}/admin/home-layout`, layout);
  }
}
