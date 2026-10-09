import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SizeChart } from './store-settings.service';

/** One chart of the size chart library. */
export interface SizeChartEntry {
  id: number;
  name: string;
  chart: SizeChart;
  /** Shown by a product when neither it nor a category above it names a chart. */
  isDefault: boolean;
  /** Products that name this chart themselves. */
  productCount: number;
  /** Categories that name this chart. */
  categoryCount: number;
}

/**
 * The shop's size charts. A product can name one, a category can name one for
 * everything in and under it, and the default covers the rest.
 *
 * In a form, a chart is chosen by id; 0 stands for "none of its own".
 */
@Injectable({ providedIn: 'root' })
export class SizeChartService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  /** Every chart, by name. Open to all staff: any form that names a chart needs the list. */
  list(): Observable<SizeChartEntry[]> {
    return this.http.get<SizeChartEntry[]>(`${this.baseUrl}/api/size-charts`);
  }

  create(name: string, chart: SizeChart): Observable<SizeChartEntry> {
    return this.http.post<SizeChartEntry>(`${this.baseUrl}/admin/size-charts`, { name, chart });
  }

  update(id: number, name: string, chart: SizeChart): Observable<SizeChartEntry> {
    return this.http.put<SizeChartEntry>(`${this.baseUrl}/admin/size-charts/${id}`, { name, chart });
  }

  setDefault(id: number): Observable<SizeChartEntry> {
    return this.http.put<SizeChartEntry>(`${this.baseUrl}/admin/size-charts/${id}/default`, null);
  }

  /** Answers with how many products and categories were using it. */
  delete(id: number): Observable<{ released: number }> {
    return this.http.delete<{ released: number }>(`${this.baseUrl}/admin/size-charts/${id}`);
  }
}
