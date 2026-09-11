import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** One recorded staff action, from GET /admin/activity. */
export interface ActivityEntry {
  id: string;
  /** Already Dhaka local time, 'yyyy-MM-dd HH:mm:ss'. */
  occurredAt: string;
  staffId: string | null;
  staffName: string;
  staffUsername: string | null;
  method: string;
  path: string;
  permKey: string | null;
  area: string | null;
  targetId: string | null;
  summary: string | null;
  status: number | null;
  /** Longer detail, e.g. every old and new price of a category price change. */
  details: string | null;
}

export interface ActivityPage {
  items: ActivityEntry[];
  total: number;
  page: number;
  size: number;
}

/** Someone who appears in the log, for the Person filter. */
export interface ActivityPerson {
  staffId: string;
  name: string;
}

/** All optional. `from` and `to` are Dhaka dates ('YYYY-MM-DD') and inclusive. */
export interface ActivityFilter {
  staffId?: string | null;
  area?: string | null;
  from?: string | null;
  to?: string | null;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class ActivityService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  /** Newest first. Empty filters are left out of the request. */
  list(filter: ActivityFilter): Observable<ActivityPage> {
    let params = new HttpParams();
    for (const [name, value] of Object.entries(filter)) {
      if (value !== null && value !== undefined && value !== '') params = params.set(name, String(value));
    }
    return this.http.get<ActivityPage>(`${this.base}/admin/activity`, { params });
  }

  people(): Observable<ActivityPerson[]> {
    return this.http.get<ActivityPerson[]>(`${this.base}/admin/activity/people`);
  }
}
