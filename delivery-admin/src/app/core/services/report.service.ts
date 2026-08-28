import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface RevenuePoint {
  date: string;            // ISO date (yyyy-MM-dd)
  revenue: number;
  orderCount: number;
}

export interface TopProduct {
  productId: number;
  productName: string;
  imageUrl: string | null;
  unitsSold: number;
  revenue: number;
}

export interface TopCategory {
  categoryId: number;
  categoryName: string;
  unitsSold: number;
  revenue: number;
}

export interface SalesSummary {
  totalOrders: number;
  deliveredOrders: number;
  cancelledOrders: number;
  returnedOrders: number;
  grossRevenue: number;
  netRevenue: number;
  averageOrderValue: number;
  uniqueCustomers: number;
}

export interface CustomerLifetimeValue {
  userId: string;
  customerName: string | null;
  email: string;
  orderCount: number;
  lifetimeRevenue: number;
  averageOrderValue: number;
  firstOrderAt: string | null;
  lastOrderAt: string | null;
}

export type Granularity = 'DAY' | 'WEEK' | 'MONTH';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  summary(from?: string, to?: string): Observable<SalesSummary> {
    return this.http.get<SalesSummary>(`${this.base}/admin/reports/summary`, {
      params: this.dateParams(from, to)
    });
  }

  revenue(from: string, to: string, granularity: Granularity = 'DAY'): Observable<RevenuePoint[]> {
    return this.http.get<RevenuePoint[]>(`${this.base}/admin/reports/revenue`, {
      params: { ...this.dateParams(from, to), granularity }
    });
  }

  topProducts(from: string, to: string, limit = 10): Observable<TopProduct[]> {
    return this.http.get<TopProduct[]>(`${this.base}/admin/reports/top-products`, {
      params: { ...this.dateParams(from, to), limit: String(limit) }
    });
  }

  topCategories(from: string, to: string, limit = 10): Observable<TopCategory[]> {
    return this.http.get<TopCategory[]>(`${this.base}/admin/reports/top-categories`, {
      params: { ...this.dateParams(from, to), limit: String(limit) }
    });
  }

  customers(limit = 50, offset = 0): Observable<CustomerLifetimeValue[]> {
    return this.http.get<CustomerLifetimeValue[]>(`${this.base}/admin/customers`, {
      params: { limit: String(limit), offset: String(offset) }
    });
  }

  customerCount(): Observable<number> {
    return this.http.get<number>(`${this.base}/admin/customers/count`);
  }

  private dateParams(from?: string, to?: string): Record<string, string> {
    const params: Record<string, string> = {};
    if (from) params['from'] = from;
    if (to)   params['to']   = to;
    return params;
  }
}
