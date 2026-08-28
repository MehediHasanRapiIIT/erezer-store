import { Component, signal, computed, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { environment } from '../../../environments/environment';
import { parseApiError } from '../../core/utils/api-error.util';

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

  // Peak delivery trend (static representative)
  readonly trendLabels = [{x:40,l:'8 AM'},{x:160,l:'12 PM'},{x:280,l:'4 PM'},{x:400,l:'8 PM'},{x:520,l:'12 AM'}];

  // Computed chart points from real daily data
  chartPoints = computed(() => {
    const d = this.data();
    if (!d || d.dailyOrders.length === 0) return '40,120 120,90 200,100 280,80 360,60 440,75 520,85';
    const maxCount = Math.max(...d.dailyOrders.map(o => o.count), 1);
    return d.dailyOrders.map((o, i) => {
      const x = 40 + i * 80;
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

  private getDateRange(filter: string): { fromDate?: string; toDate?: string } {
    const today = new Date();
    const fmt = (d: Date) => d.toISOString().split('T')[0];
    if (filter === 'TODAY') return { fromDate: fmt(today), toDate: fmt(today) };
    if (filter === 'LAST_7') { const d = new Date(today); d.setDate(today.getDate() - 7); return { fromDate: fmt(d), toDate: fmt(today) }; }
    if (filter === 'LAST_30') { const d = new Date(today); d.setDate(today.getDate() - 30); return { fromDate: fmt(d), toDate: fmt(today) }; }
    if (filter === 'THIS_MONTH') { const d = new Date(today.getFullYear(), today.getMonth(), 1); return { fromDate: fmt(d), toDate: fmt(today) }; }
    return {};
  }

  formatRevenue(amount: number): string {
    if (amount >= 1000000) return '৳ ' + (amount / 1000000).toFixed(2) + 'M';
    if (amount >= 1000) return '৳ ' + amount.toLocaleString('en-BD');
    return '৳ ' + amount;
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
    const days = ['Sun','Mon','Tue','Wed','Thu','Fri','Sat'];
    return days[new Date(dateStr).getDay()];
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

  // Dot Y position for chart points
  dotY(count: number): number {
    const d = this.data();
    if (!d || d.dailyOrders.length === 0) return 100;
    const maxCount = Math.max(...d.dailyOrders.map(o => o.count), 1);
    return 130 - Math.round((count / maxCount) * 100);
  }
}
