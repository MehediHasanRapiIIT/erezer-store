import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AdminSearchResponse, CustomerSearchResponse } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class SearchService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  adminSearch(query: string, limit = 5): Observable<AdminSearchResponse> {
    const params = new HttpParams().set('q', query).set('limit', String(limit));
    return this.http.get<AdminSearchResponse>(`${this.baseUrl}/admin/search`, { params });
  }

  customerSearch(query: string, limit = 5): Observable<CustomerSearchResponse> {
    const params = new HttpParams().set('q', query).set('limit', String(limit));
    return this.http.get<CustomerSearchResponse>(`${this.baseUrl}/api/search`, { params });
  }
}
