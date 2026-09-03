import { Component, signal, computed, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { environment } from '../../../environments/environment';
import { parseApiError } from '../../core/utils/api-error.util';
import { addDays, businessToday, formatTaka, formatTakaCompact, parseIsoDate } from '../../core/utils/business-date.util';

const PAYMENT_LABELS: Record<string, string> = {
  CASH: 'Cash on Delivery', COD: 'Cash on Delivery', BKASH: 'bKash', NAGAD: 'Nagad',
  ROCKET: 'Rocket', CARD: 'Card', UNKNOWN: 'Not recorded',
};

interface DailyOrder { date: string; count: number; revenue: number; }
interface CategoryRevenue { categoryName: string; revenue: number; orderCount: number; }

interface AnalyticsData {
  totalRevenue: number;
  totalOrders: number;
  cancelledOrders: number;
  completedOrders: number;
  pendingOrders: number;
  completionRate: number;
  cancellationRate: number;
  avgOrderValue: number;
  stockCriticalLow: number;
  stockOutOfStock: number;
  stockReorderPending: number;
  ordersByStatus: { status: string; count: number }[];
  ordersByPayment: { method: string; count: number; revenue: number }[];
  dailyOrders: DailyOrder[];
  topCategories: CategoryRevenue[];
  topProducts: { id: number; name: string; imageUrl: string; price: number; stockQuantity: number; stockStatus: string }[];
}

@Component({
  selector: 'app-analytics',
  standalone: true,
  imports: [SidebarComponent, RouterLink, FormsModule],
  templateUrl: './analytics.component.html',
})
export class AnalyticsComponent implements OnInit {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  data = signal<AnalyticsData | null>(null);
  isLoading = signal(true);
  errorMessage = signal('');

  // Date filter
  filterDate = signal<string>('ALL');

  // SVG ring chart helpers
  readonly ringRadius = 40;
  readonly ringCircumference = 2 * Math.PI * this.ringRadius;

  // Computed chart points from real daily data
  chartPoints = computed(() => {
    const d = this.data();
    if (!d || d.dailyOrders.length === 0) return '40,120 120,90 200,100 280,80 360,60 440,75 520,85';
    const maxCount = Math.max(...d.dailyOrders.map(o => o.count), 1);
    return d.dailyOrders.map((o, i) => {
      const x = this.dotX(i);
      const y = 130 - Math.round((o.count / maxCount) * 100);
      return `${x},${y}`;
    }).join(' ');
  });

  chartArea = computed(() => {
    const pts = this.chartPoints();
    const parts = pts.split(' ');
    const last = parts[parts.length - 1].split(',');
    const first = parts[0].split(',');
    return `${pts} ${last[0]},140 ${first[0]},140`;
  });

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.isLoading.set(true);
    this.errorMessage.set('');
    const { fromDate, toDate } = this.getDateRange(this.filterDate());
    let url = `${this.baseUrl}/admin/dashboard/analytics`;
    const params: string[] = [];
    if (fromDate) params.push(`fromDate=${fromDate}`);
    if (toDate) params.push(`toDate=${toDate}`);
    if (params.length) url += '?' + params.join('&');

    this.http.get<AnalyticsData>(url).subscribe({
      next: (d) => { this.data.set(d); this.isLoading.set(false); },
      error: (err) => { this.errorMessage.set(parseApiError(err)); this.isLoading.set(false); },
    });
  }

  onDateFilterChange(val: string): void {
    this.filterDate.set(val);
    this.loadData();
  }

  /**
   * Windows are business-local (Asia/Dhaka) and inclusive of today.
   * "Last 7 days" is today plus the six before it, matching the trend chart.
   */
  private getDateRange(filter: string): { fromDate?: string; toDate?: string } {
    const today = businessToday();
    if (filter === 'TODAY') return { fromDate: today, toDate: today };
    if (filter === 'LAST_7') return { fromDate: addDays(today, -6), toDate: today };
    if (filter === 'LAST_30') return { fromDate: addDays(today, -29), toDate: today };
    if (filter === 'THIS_MONTH') return { fromDate: today.slice(0, 8) + '01', toDate: today };
    return {};
  }

  /** Current window as text for the header. */
  rangeLabel(): string {
    const { fromDate, toDate } = this.getDateRange(this.filterDate());
    if (!fromDate) return 'All time';
    return fromDate === toDate ? fromDate : `${fromDate} → ${toDate}`;
  }

  formatRevenue(amount: number): string {
    return formatTakaCompact(amount);
  }

  formatTakaExact(amount: number): string {
    return formatTaka(amount, 2);
  }

  paymentLabel(method: string): string {
    return PAYMENT_LABELS[method] ?? method;
  }

  /** Everything on the page as CSV. */
  exportCsv(): void {
    const d = this.data();
    if (!d) return;
    const rows: (string | number)[][] = [
      ['Erezer analytics', this.rangeLabel(), 'Asia/Dhaka'],
      [],
      ['Net revenue', d.totalRevenue], ['Placed orders', d.totalOrders], ['Delivered orders', d.completedOrders],
      ['Cancelled orders', d.cancelledOrders], ['Pending orders', d.pendingOrders],
      ['Delivery rate %', d.completionRate], ['Cancellation rate %', d.cancellationRate], ['Average order value', d.avgOrderValue],
      [],
      ['Date', 'Valid orders', 'Net revenue'],
      ...d.dailyOrders.map(o => [o.date, o.count, o.revenue] as (string | number)[]),
      [],
      ['Status', 'Orders'],
      ...d.ordersByStatus.map(s => [s.status, s.count] as (string | number)[]),
      [],
      ['Payment method', 'Valid orders', 'Net revenue'],
      ...d.ordersByPayment.map(p => [this.paymentLabel(p.method), p.count, p.revenue] as (string | number)[]),
      [],
      ['Category', 'Sales value', 'Orders'],
      ...d.topCategories.map(c => [c.categoryName, c.revenue, c.orderCount] as (string | number)[]),
    ];
    const csv = rows.map(r => r.map(c => {
      const s = String(c ?? '');
      return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
    }).join(',')).join('\r\n');
    const url = URL.createObjectURL(new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = `erezer-analytics-${this.filterDate().toLowerCase()}.csv`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  ringDashArray(pct: number): string {
    const filled = (pct / 100) * this.ringCircumference;
    return `${filled} ${this.ringCircumference - filled}`;
  }

  getBarHeight(count: number): number {
    const d = this.data();
    if (!d || d.totalOrders === 0) return 10;
    return Math.max(8, Math.round((count / d.totalOrders) * 120));
  }

  maxCategoryRevenue(): number {
    const d = this.data();
    if (!d || d.topCategories.length === 0) return 1;
    return Math.max(...d.topCategories.map(c => c.revenue), 1);
  }

  stockBadgeClass(status: string): string {
    if (status === 'IN_STOCK') return 'bg-emerald-100 text-emerald-700';
    if (status === 'LOW_STOCK') return 'bg-yellow-100 text-yellow-700';
    return 'bg-red-100 text-red-600';
  }

  dayLabel(dateStr: string): string {
    // Parsed as a local calendar date: new Date('yyyy-MM-dd') is UTC midnight
    // and shows the previous weekday in any zone west of Greenwich.
    const days = ['Sun','Mon','Tue','Wed','Thu','Fri','Sat'];
    return days[parseIsoDate(dateStr).getDay()];
  }

  // Colored bars for Orders by Status chart
  statusBarColor(index: number): string {
    const colors = ['bg-blue-500','bg-yellow-500','bg-emerald-500','bg-red-500','bg-violet-500','bg-orange-500','bg-pink-500'];
    return colors[index % colors.length];
  }

  // Payment method bar/dot colors
  paymentDotColor(index: number): string {
    const colors = ['bg-emerald-500','bg-blue-500','bg-violet-500','bg-orange-500','bg-pink-500'];
    return colors[index % colors.length];
  }

  paymentBarColor(index: number): string {
    const colors = ['bg-emerald-500','bg-blue-500','bg-violet-500','bg-orange-500','bg-pink-500'];
    return colors[index % colors.length];
  }

  // Category rank badge colors
  rankBadgeColor(index: number): string {
    const colors = ['bg-orange-500','bg-gray-400','bg-amber-600','bg-blue-400','bg-violet-400'];
    return colors[index] ?? 'bg-gray-300';
  }

  categoryBarColor(index: number): string {
    const colors = ['bg-orange-500','bg-blue-500','bg-emerald-500','bg-violet-500','bg-pink-500'];
    return colors[index % colors.length];
  }

  // Dot X position: points spread across the 40…520 plot width.
  dotX(index: number): number {
    const n = this.data()?.dailyOrders.length ?? 0;
    if (n <= 1) return 280;
    return Math.round(40 + (index * 480) / (n - 1));
  }

  // Dot Y position for chart points
  dotY(count: number): number {
    const d = this.data();
    if (!d || d.dailyOrders.length === 0) return 100;
    const maxCount = Math.max(...d.dailyOrders.map(o => o.count), 1);
    return 130 - Math.round((count / maxCount) * 100);
  }
}
