import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { catchError, forkJoin, of } from 'rxjs';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import {
  Granularity,
  ReportService,
  RevenuePoint,
  SalesSummary,
  TopCategory,
  TopProduct,
} from '../../core/services/report.service';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CurrencyPipe, DecimalPipe, FormsModule, SidebarComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between flex-shrink-0">
          <h1 class="text-lg font-bold text-gray-900">Sales reports</h1>
          <div class="flex items-center gap-3 text-sm">
            <label class="text-gray-500">From:</label>
            <input type="date" [(ngModel)]="from" (ngModelChange)="reload()"
              class="rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-sm" />
            <label class="text-gray-500">To:</label>
            <input type="date" [(ngModel)]="to" (ngModelChange)="reload()"
              class="rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-sm" />
            <select [(ngModel)]="granularity" (ngModelChange)="reloadRevenue()"
              class="rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-sm">
              <option value="DAY">Daily</option>
              <option value="WEEK">Weekly</option>
              <option value="MONTH">Monthly</option>
            </select>
          </div>
        </header>

        <main class="flex-1 overflow-y-auto p-6">
          <div class="max-w-6xl mx-auto space-y-6">

            <!-- Summary cards -->
            <section class="grid gap-4 md:grid-cols-4">
              @if (summary(); as s) {
                <article class="rounded-xl border border-gray-200 bg-white p-4">
                  <p class="text-xs uppercase tracking-wide text-gray-400">Net revenue</p>
                  <p class="mt-1 text-2xl font-bold">{{ s.netRevenue | currency: 'BDT' : 'symbol-narrow' : '1.0-0' }}</p>
                  <p class="mt-1 text-xs text-gray-500">Gross {{ s.grossRevenue | currency: 'BDT' : 'symbol-narrow' : '1.0-0' }}</p>
                </article>
                <article class="rounded-xl border border-gray-200 bg-white p-4">
                  <p class="text-xs uppercase tracking-wide text-gray-400">Orders</p>
                  <p class="mt-1 text-2xl font-bold">{{ s.totalOrders | number }}</p>
                  <p class="mt-1 text-xs text-gray-500">
                    <span class="text-emerald-600">{{ s.deliveredOrders }} delivered</span>
                    &middot; <span class="text-red-500">{{ s.cancelledOrders }} cancelled</span>
                    &middot; <span class="text-amber-600">{{ s.returnedOrders }} returned</span>
                  </p>
                </article>
                <article class="rounded-xl border border-gray-200 bg-white p-4">
                  <p class="text-xs uppercase tracking-wide text-gray-400">AOV</p>
                  <p class="mt-1 text-2xl font-bold">{{ s.averageOrderValue | currency: 'BDT' : 'symbol-narrow' : '1.0-0' }}</p>
                  <p class="mt-1 text-xs text-gray-500">Per delivered order</p>
                </article>
                <article class="rounded-xl border border-gray-200 bg-white p-4">
                  <p class="text-xs uppercase tracking-wide text-gray-400">Customers</p>
                  <p class="mt-1 text-2xl font-bold">{{ s.uniqueCustomers | number }}</p>
                  <p class="mt-1 text-xs text-gray-500">Unique in window</p>
                </article>
              }
            </section>

            <!-- Revenue sparkline -->
            <section class="rounded-xl border border-gray-200 bg-white p-5">
              <header class="mb-3 flex items-baseline justify-between">
                <div>
                  <h2 class="font-bold text-gray-900">Revenue over time</h2>
                  <p class="text-xs text-gray-500">{{ revenue().length }} {{ granularity.toLowerCase() }} bucket(s)</p>
                </div>
                @if (revenue().length > 0) {
                  <p class="text-xs text-gray-500">
                    Max: <strong>{{ maxRevenue() | currency: 'BDT' : 'symbol-narrow' : '1.0-0' }}</strong>
                  </p>
                }
              </header>
              @if (revenue().length === 0) {
                <p class="text-sm text-gray-400">No data in this window.</p>
              } @else {
                <svg [attr.viewBox]="'0 0 ' + chartWidth + ' ' + chartHeight" class="w-full h-32"
                  preserveAspectRatio="none">
                  <polyline
                    [attr.points]="sparkPoints()"
                    fill="none"
                    stroke="#2563eb"
                    stroke-width="2" />
                </svg>
                <table class="mt-4 w-full text-sm">
                  <thead>
                    <tr class="border-b border-gray-100 text-xs uppercase text-gray-400">
                      <th class="py-2 text-left">Bucket</th>
                      <th class="py-2 text-right">Orders</th>
                      <th class="py-2 text-right">Revenue</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-gray-50">
                    @for (point of revenue(); track point.date) {
                      <tr>
                        <td class="py-2">{{ point.date }}</td>
                        <td class="py-2 text-right">{{ point.orderCount }}</td>
                        <td class="py-2 text-right">{{ point.revenue | currency: 'BDT' : 'symbol-narrow' : '1.0-0' }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              }
            </section>

            <!-- Top products + categories -->
            <section class="grid gap-6 lg:grid-cols-2">
              <article class="rounded-xl border border-gray-200 bg-white p-5">
                <h2 class="mb-3 font-bold text-gray-900">Top products</h2>
                <table class="w-full text-sm">
                  <thead>
                    <tr class="border-b border-gray-100 text-xs uppercase text-gray-400">
                      <th class="py-2 text-left">Product</th>
                      <th class="py-2 text-right">Units</th>
                      <th class="py-2 text-right">Revenue</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-gray-50">
                    @for (p of topProducts(); track p.productId) {
                      <tr>
                        <td class="py-2">{{ p.productName }}</td>
                        <td class="py-2 text-right">{{ p.unitsSold }}</td>
                        <td class="py-2 text-right">{{ p.revenue | currency: 'BDT' : 'symbol-narrow' : '1.0-0' }}</td>
                      </tr>
                    } @empty {
                      <tr><td colspan="3" class="py-3 text-center text-gray-400">No sales in this window.</td></tr>
                    }
                  </tbody>
                </table>
              </article>

              <article class="rounded-xl border border-gray-200 bg-white p-5">
                <h2 class="mb-3 font-bold text-gray-900">Top categories</h2>
                <table class="w-full text-sm">
                  <thead>
                    <tr class="border-b border-gray-100 text-xs uppercase text-gray-400">
                      <th class="py-2 text-left">Category</th>
                      <th class="py-2 text-right">Units</th>
                      <th class="py-2 text-right">Revenue</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-gray-50">
                    @for (c of topCategories(); track c.categoryId) {
                      <tr>
                        <td class="py-2">{{ c.categoryName }}</td>
                        <td class="py-2 text-right">{{ c.unitsSold }}</td>
                        <td class="py-2 text-right">{{ c.revenue | currency: 'BDT' : 'symbol-narrow' : '1.0-0' }}</td>
                      </tr>
                    } @empty {
                      <tr><td colspan="3" class="py-3 text-center text-gray-400">No sales in this window.</td></tr>
                    }
                  </tbody>
                </table>
              </article>
            </section>
          </div>
        </main>
      </div>
    </div>
  `,
})
export class ReportsComponent implements OnInit {
  private readonly api = inject(ReportService);

  readonly chartWidth  = 600;
  readonly chartHeight = 120;

  protected from        = isoDaysAgo(30);
  protected to          = isoToday();
  protected granularity: Granularity = 'DAY';

  readonly summary       = signal<SalesSummary | null>(null);
  readonly revenue       = signal<RevenuePoint[]>([]);
  readonly topProducts   = signal<TopProduct[]>([]);
  readonly topCategories = signal<TopCategory[]>([]);

  readonly maxRevenue = computed(() => {
    return this.revenue().reduce((m, p) => p.revenue > m ? p.revenue : m, 0);
  });

  /** Polyline points for the SVG sparkline, normalised to chart bounds. */
  readonly sparkPoints = computed(() => {
    const pts = this.revenue();
    if (pts.length === 0) return '';
    const max = Math.max(this.maxRevenue(), 1);
    const w   = this.chartWidth;
    const h   = this.chartHeight;
    return pts.map((p, i) => {
      const x = pts.length === 1 ? w / 2 : (i / (pts.length - 1)) * w;
      const y = h - (p.revenue / max) * h;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    }).join(' ');
  });

  ngOnInit(): void {
    this.reload();
  }

  protected reload(): void {
    forkJoin({
      summary:       this.api.summary(this.from, this.to).pipe(catchError(() => of(null))),
      revenue:       this.api.revenue(this.from, this.to, this.granularity).pipe(catchError(() => of([] as RevenuePoint[]))),
      topProducts:   this.api.topProducts(this.from, this.to).pipe(catchError(() => of([] as TopProduct[]))),
      topCategories: this.api.topCategories(this.from, this.to).pipe(catchError(() => of([] as TopCategory[]))),
    }).subscribe(({ summary, revenue, topProducts, topCategories }) => {
      this.summary.set(summary);
      this.revenue.set(revenue);
      this.topProducts.set(topProducts);
      this.topCategories.set(topCategories);
    });
  }

  protected reloadRevenue(): void {
    this.api.revenue(this.from, this.to, this.granularity)
      .pipe(catchError(() => of([] as RevenuePoint[])))
      .subscribe((rev) => this.revenue.set(rev));
  }
}

function isoToday(): string {
  return new Date().toISOString().slice(0, 10);
}

function isoDaysAgo(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}
