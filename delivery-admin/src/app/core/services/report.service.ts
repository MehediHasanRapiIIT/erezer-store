import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface RevenuePoint {
  date: string;            // ISO date (yyyy-MM-dd), start of the bucket, business-local
  revenue: number;         // net revenue of counted orders
  orderCount: number;      // counted orders (cancelled & returned excluded)
  cancelledOrders: number;
}

export interface TopProduct {
  productId: number;
  productName: string;
  imageUrl: string | null;
  unitsSold: number;
  revenue: number;         // sales value (qty × price + surcharge)
  orderCount: number;
}

export interface TopCategory {
  categoryId: number;
  categoryName: string;
  unitsSold: number;
  revenue: number;
  orderCount: number;
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

export type Granularity = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR';

/** The calendar periods a business report can cover. */
export type PeriodType = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR' | 'FISCAL_YEAR';

/**
 * One period's figures. Attribution is by order date unless the name says
 * "InPeriod", which is by the delivery / cancellation event date.
 */
export interface PeriodMetrics {
  placedOrders: number;
  orders: number;
  deliveredOrders: number;
  cancelledOrders: number;
  returnedOrders: number;
  pendingOrders: number;
  inProgressOrders: number;
  unitsSold: number;

  grossSales: number;
  discounts: number;
  shipping: number;
  surcharges: number;
  vat: number;
  netRevenue: number;
  deliveredRevenue: number;
  averageOrderValue: number;

  cancelledValue: number;
  returnedValue: number;

  uniqueCustomers: number;
  newCustomers: number;

  cancellationRate: number;
  returnRate: number;
  deliveryRate: number;

  deliveredInPeriodOrders: number;
  deliveredInPeriodRevenue: number;
  cancelledInPeriodOrders: number;
  cancelledInPeriodValue: number;
}

export interface ReportBucket {
  bucketStart: string;     // business-local ISO date-time
  label: string;
  placedOrders: number;
  orders: number;
  cancelledOrders: number;
  netRevenue: number;
}

export interface StatusCount {
  status: string;
  count: number;
  value: number;
}

export interface PaymentSplit {
  method: string;
  orders: number;
  revenue: number;
  deliveredOrders: number;
  deliveredRevenue: number;
  undeliveredOrders: number;
  undeliveredValue: number;
  cancelledOrders: number;
}

export interface PeriodReport {
  type: PeriodType;
  label: string;
  start: string;
  end: string;
  zone: string;
  weekStart: string;
  currency: string;
  generatedAt: string;
  complete: boolean;

  current: PeriodMetrics;
  previous: PeriodMetrics;
  previousLabel: string;
  previousStart: string;
  previousEnd: string;

  bucketUnit: 'hour' | 'day' | 'month';
  breakdown: ReportBucket[];

  byStatus: StatusCount[];
  byPayment: PaymentSplit[];
  topProducts: TopProduct[];
  topCategories: TopCategory[];
}

@Injectable({ providedIn: 'root' })
export class ReportService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  /** Full day / week / month / year / fiscal-year report containing `date` (today when omitted). */
  period(type: PeriodType, date?: string): Observable<PeriodReport> {
    const params: Record<string, string> = { type };
    if (date) params['date'] = date;
    return this.http.get<PeriodReport>(`${this.base}/admin/reports/period`, { params });
  }

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
