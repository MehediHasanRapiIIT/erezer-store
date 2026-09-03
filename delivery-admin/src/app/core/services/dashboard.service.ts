import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { HomeResponse } from '../models/api.models';

/**
 * Headline tiles. Same reporting engine as the Reports page: revenue is net
 * revenue of orders that are not cancelled or returned; "today", "week"
 * (Sunday–Saturday) and "month" are Asia/Dhaka periods.
 */
export interface DashboardStats {
  totalOrders: number;
  validOrders: number;
  totalRevenue: number;
  pendingOrders: number;
  cancelledOrders: number;
  activeRiders: number;

  todayOrders: number;
  todayRevenue: number;
  yesterdayOrders: number;
  yesterdayRevenue: number;

  weekOrders: number;
  weekRevenue: number;
  lastWeekOrders: number;
  lastWeekRevenue: number;

  monthOrders: number;
  monthRevenue: number;
  lastMonthOrders: number;
  lastMonthRevenue: number;

  lowStockProducts: number;
  outOfStockProducts: number;

  asOf: string;
  zone: string;
  currency: string;
}

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  getHomeData(): Observable<HomeResponse> {
    return this.http.get<HomeResponse>(`${this.baseUrl}/app/home`);
  }

  getStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${this.baseUrl}/admin/dashboard/stats`);
  }
}
