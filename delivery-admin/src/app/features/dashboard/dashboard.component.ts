import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DashboardService, DashboardStats } from '../../core/services/dashboard.service';
import { ReportService, RevenuePoint, TopProduct } from '../../core/services/report.service';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { parseApiError } from '../../core/utils/api-error.util';
import {
  addDays, addMonths, businessToday, formatTaka, formatTakaCompact, parseIsoDate, percentChange, weekdayShort,
} from '../../core/utils/business-date.util';

type Trend = 'Weekly' | 'Monthly';

interface ChartPoint { x: number; y: number; label: string; revenue: number; orders: number; }

/**
 * Overview page. Tiles come from /admin/dashboard/stats and the trend chart
 * from /admin/reports/revenue, both driven by the same SQL definitions as
 * the Reports page, so a figure here always matches the report for the same
 * period.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [SidebarComponent, RouterLink],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit {
  private dashboardService = inject(DashboardService);
  private reportService = inject(ReportService);

  readonly today = businessToday();

  stats = signal<DashboardStats | null>(null);
  errorMessage = signal('');

  activeTab = signal<Trend>('Weekly');
  trend = signal<RevenuePoint[]>([]);
  trendLoading = signal(false);

  /** Best sellers over the last 30 days, by units on valid orders. */
  readonly bestSellersFrom = addDays(this.today, -29);
  bestSellers = signal<TopProduct[]>([]);

  // SVG geometry shared with the template (viewBox 0 0 560 180).
  private readonly x0 = 40;
  private readonly x1 = 520;
  private readonly yTop = 20;
  private readonly yBase = 150;

  chartPointsList = computed<ChartPoint[]>(() => {
    const pts = this.trend();
    if (pts.length === 0) return [];
    const max = Math.max(...pts.map(p => p.revenue), 1);
    const stepX = pts.length > 1 ? (this.x1 - this.x0) / (pts.length - 1) : 0;
    return pts.map((p, i) => ({
      x: Math.round(this.x0 + i * stepX),
      y: Math.round(this.yBase - ((this.yBase - this.yTop) * p.revenue) / max),
      label: this.activeTab() === 'Weekly'
        ? weekdayShort(p.date)
        : parseIsoDate(p.date).toLocaleDateString('en-GB', { month: 'short' }),
      revenue: p.revenue,
      orders: p.orderCount,
    }));
  });

  chartPoints = computed(() => this.chartPointsList().map(p => `${p.x},${p.y}`).join(' '));

  chartArea = computed(() => {
    const pts = this.chartPointsList();
    if (pts.length === 0) return '';
    return `${this.chartPoints()} ${pts[pts.length - 1].x},${this.yBase + 10} ${pts[0].x},${this.yBase + 10}`;
  });

  trendTotal = computed(() => this.trend().reduce((s, p) => s + p.revenue, 0));
  trendOrders = computed(() => this.trend().reduce((s, p) => s + p.orderCount, 0));

  todayDelta = computed(() => {
    const s = this.stats();
    return s ? percentChange(s.todayRevenue, s.yesterdayRevenue) : null;
  });
  weekDelta = computed(() => {
    const s = this.stats();
    return s ? percentChange(s.weekRevenue, s.lastWeekRevenue) : null;
  });
  monthDelta = computed(() => {
    const s = this.stats();
    return s ? percentChange(s.monthRevenue, s.lastMonthRevenue) : null;
  });

  ngOnInit(): void {
    this.dashboardService.getStats().subscribe({
      next: (s) => this.stats.set(s),
      error: (err) => this.errorMessage.set(parseApiError(err)),
    });

    this.loadTrend();

    this.reportService.topProducts(this.bestSellersFrom, this.today, 5).subscribe({
      next: (rows) => this.bestSellers.set(rows),
      error: (err) => this.errorMessage.set(parseApiError(err)),
    });
  }

  setTab(tab: Trend): void {
    if (this.activeTab() === tab) return;
    this.activeTab.set(tab);
    this.loadTrend();
  }

  /** Weekly = the last 7 days by day; Monthly = the last 12 months by month. */
  private loadTrend(): void {
    this.trendLoading.set(true);
    const tab = this.activeTab();
    const from = tab === 'Weekly'
      ? addDays(this.today, -6)
      : addMonths(this.today.slice(0, 8) + '01', -11);
    this.reportService.revenue(from, this.today, tab === 'Weekly' ? 'DAY' : 'MONTH').subscribe({
      next: (pts) => {
        if (this.activeTab() !== tab) return;
        this.trend.set(pts);
        this.trendLoading.set(false);
      },
      error: (err) => { this.errorMessage.set(parseApiError(err)); this.trendLoading.set(false); },
    });
  }

  formatRevenue(amount: number): string {
    return formatTakaCompact(amount);
  }

  taka(amount: number): string {
    return formatTaka(amount);
  }

  deltaText(delta: number | null): string {
    if (delta === null) return 'no comparison yet';
    if (delta === 0) return 'unchanged';
    return `${delta > 0 ? '▲' : '▼'} ${Math.abs(delta)}%`;
  }

  deltaClass(delta: number | null): string {
    if (delta === null || delta === 0) return 'text-gray-400';
    return delta > 0 ? 'text-emerald-600' : 'text-red-600';
  }
}
