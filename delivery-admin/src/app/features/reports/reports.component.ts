import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import {
  PaymentSplit, PeriodMetrics, PeriodReport, PeriodType, ReportBucket, ReportService,
} from '../../core/services/report.service';
import { parseApiError } from '../../core/utils/api-error.util';
import {
  addDays, addMonths, addYears, businessToday, formatDate, formatTaka, formatTakaCompact, percentChange,
} from '../../core/utils/business-date.util';

interface Tab { type: PeriodType; label: string; hint: string; }

interface Kpi {
  key: keyof PeriodMetrics;
  label: string;
  money: boolean;
  current: number;
  previous: number;
  delta: number | null;
  /** true when a rise is bad news (cancellations). */
  inverse: boolean;
  hint?: string;
}

interface ChartBar {
  x: number; y: number; w: number; h: number;
  label: string; showLabel: boolean;
  orders: number; cancelled: number; revenue: number;
}

const PAYMENT_LABELS: Record<string, string> = {
  CASH: 'Cash on Delivery', COD: 'Cash on Delivery', BKASH: 'bKash', NAGAD: 'Nagad',
  ROCKET: 'Rocket', CARD: 'Card', SSLCOMMERZ: 'SSLCommerz', UNKNOWN: 'Not recorded',
};

const STATUS_ORDER = ['PLACED', 'ACCEPTED', 'IN_PRODUCTION', 'PROCESSING', 'SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'RETURNED', 'CANCELLED'];

/**
 * Business reports: one day, one week (Sunday–Saturday), one month, one
 * calendar year or one fiscal year (July–June), with the previous period
 * alongside. Every figure is fetched from a single backend call so the page
 * can never mix numbers from two different moments.
 */
@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [FormsModule, SidebarComponent],
  styles: [`
    @media print {
      app-sidebar, .no-print { display: none !important; }
      .print-root { height: auto !important; overflow: visible !important; }
      main { overflow: visible !important; padding: 0 !important; }
    }
  `],
  template: `
    <div class="print-root flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between flex-shrink-0">
          <div>
            <h1 class="text-lg font-bold text-gray-900">Business reports</h1>
            <p class="text-[11px] text-gray-400 leading-none">Asia/Dhaka · week starts Sunday · fiscal year July–June · amounts in ৳ (BDT)</p>
          </div>
          <div class="no-print flex items-center gap-2">
            <button type="button" (click)="exportCsv()" [disabled]="!report()"
              class="px-3 py-1.5 text-sm font-medium rounded-lg border border-gray-200 bg-white text-gray-700 hover:bg-gray-50 disabled:opacity-50">
              Export CSV
            </button>
            <button type="button" (click)="print()" [disabled]="!report()"
              class="px-3 py-1.5 text-sm font-medium rounded-lg bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-50">
              Print
            </button>
          </div>
        </header>

        <main class="flex-1 overflow-y-auto p-6">
          <div class="max-w-7xl mx-auto space-y-6">

            <!-- Period picker -->
            <section class="no-print bg-white rounded-xl border border-gray-200 p-4 flex flex-wrap items-center gap-3">
              <div class="flex rounded-lg border border-gray-200 overflow-hidden text-sm font-medium">
                @for (t of tabs; track t.type) {
                  <button type="button" (click)="setType(t.type)" [title]="t.hint"
                    class="px-3.5 py-2 transition-colors border-r border-gray-200 last:border-r-0"
                    [class.bg-gray-900]="type() === t.type" [class.text-white]="type() === t.type"
                    [class.bg-white]="type() !== t.type" [class.text-gray-600]="type() !== t.type">
                    {{ t.label }}
                  </button>
                }
              </div>

              <div class="flex items-center gap-1">
                <button type="button" (click)="step(-1)" class="w-9 h-9 rounded-lg border border-gray-200 bg-white hover:bg-gray-50 text-gray-600" title="Previous">‹</button>
                <input type="date" [ngModel]="anchor()" (ngModelChange)="setAnchor($event)" [max]="today"
                  class="h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm" />
                <button type="button" (click)="step(1)" [disabled]="!canStepForward()"
                  class="w-9 h-9 rounded-lg border border-gray-200 bg-white hover:bg-gray-50 text-gray-600 disabled:opacity-40" title="Next">›</button>
                <button type="button" (click)="setAnchor(today)" class="ml-1 h-9 px-3 rounded-lg border border-gray-200 bg-white hover:bg-gray-50 text-sm text-gray-700">Today</button>
              </div>

              @if (report(); as r) {
                <div class="ml-auto text-right">
                  <p class="text-base font-bold text-gray-900">{{ r.label }}</p>
                  <p class="text-xs text-gray-500">
                    {{ formatDate(r.start) }} – {{ formatDate(r.end) }}
                    @if (!r.complete) { <span class="ml-1 rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-semibold text-amber-700">in progress</span> }
                    · vs {{ r.previousLabel }}
                  </p>
                </div>
              }
            </section>

            @if (errorMessage()) {
              <div class="px-4 py-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{{ errorMessage() }}</div>
            }
            @if (loading() && !report()) {
              <p class="text-sm text-gray-400 py-10 text-center">Loading report…</p>
            }

            @if (report(); as r) {
              <!-- Print header -->
              <div class="hidden print:block">
                <h2 class="text-xl font-bold">Erezer — {{ tabLabel() }} report: {{ r.label }}</h2>
                <p class="text-xs text-gray-500">{{ formatDate(r.start) }} – {{ formatDate(r.end) }} · generated {{ r.generatedAt.replace('T', ' ').slice(0, 16) }} ({{ r.zone }})</p>
              </div>

              <!-- KPI cards -->
              <section class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" [class.opacity-60]="loading()">
                @for (k of kpis(); track k.key) {
                  <article class="rounded-xl border border-gray-200 bg-white p-4">
                    <p class="text-xs uppercase tracking-wide text-gray-400">{{ k.label }}</p>
                    <p class="mt-1 text-2xl font-bold text-gray-900">{{ k.money ? taka(k.current) : num(k.current) }}</p>
                    <p class="mt-1 text-xs text-gray-500 flex items-center gap-1.5">
                      @if (k.delta !== null) {
                        <span class="font-semibold" [class]="deltaClass(k.delta, k.inverse)">{{ k.delta > 0 ? '▲' : k.delta < 0 ? '▼' : '•' }} {{ abs(k.delta) }}%</span>
                      } @else {
                        <span class="text-gray-400">—</span>
                      }
                      <span>vs {{ k.money ? taka(k.previous) : num(k.previous) }} {{ r.previousLabel }}</span>
                    </p>
                    @if (k.hint) { <p class="mt-1 text-[11px] text-gray-400">{{ k.hint }}</p> }
                  </article>
                }
              </section>

              <!-- Chart -->
              <section class="rounded-xl border border-gray-200 bg-white p-5">
                <header class="mb-3 flex items-baseline justify-between">
                  <div>
                    <h2 class="font-bold text-gray-900">Net revenue by {{ r.bucketUnit }}</h2>
                    <p class="text-xs text-gray-500">{{ r.breakdown.length }} {{ r.bucketUnit }}s · cancelled and returned orders excluded</p>
                  </div>
                  <p class="text-xs text-gray-500">Peak {{ r.bucketUnit }}: <strong>{{ taka(maxBucket()) }}</strong></p>
                </header>
                <svg [attr.viewBox]="'0 0 ' + chartW + ' ' + chartH" class="w-full" style="height: 220px" preserveAspectRatio="none">
                  @for (g of gridLines; track g) {
                    <line x1="0" [attr.y1]="g" [attr.x2]="chartW" [attr.y2]="g" stroke="#f3f4f6" stroke-width="1" />
                  }
                  @for (b of bars(); track b.x) {
                    <rect [attr.x]="b.x" [attr.y]="b.y" [attr.width]="b.w" [attr.height]="b.h" rx="2" fill="#10b981">
                      <title>{{ b.label }}: {{ taka(b.revenue) }} · {{ b.orders }} orders · {{ b.cancelled }} cancelled</title>
                    </rect>
                    @if (b.showLabel) {
                      <text [attr.x]="b.x + b.w / 2" [attr.y]="chartH - 4" text-anchor="middle" fill="#9ca3af" font-size="10">{{ b.label }}</text>
                    }
                  }
                </svg>
                <details class="mt-3">
                  <summary class="cursor-pointer text-xs font-medium text-gray-500">Show table</summary>
                  <div class="overflow-x-auto">
                    <table class="mt-2 w-full text-sm">
                      <thead>
                        <tr class="border-b border-gray-100 text-xs uppercase text-gray-400">
                          <th class="py-2 text-left">{{ r.bucketUnit }}</th>
                          <th class="py-2 text-right">Placed</th>
                          <th class="py-2 text-right">Valid</th>
                          <th class="py-2 text-right">Cancelled</th>
                          <th class="py-2 text-right">Net revenue</th>
                        </tr>
                      </thead>
                      <tbody class="divide-y divide-gray-50">
                        @for (b of r.breakdown; track b.bucketStart) {
                          <tr [class.text-gray-300]="b.placedOrders === 0">
                            <td class="py-1.5">{{ b.label }}</td>
                            <td class="py-1.5 text-right">{{ b.placedOrders }}</td>
                            <td class="py-1.5 text-right">{{ b.orders }}</td>
                            <td class="py-1.5 text-right">{{ b.cancelledOrders }}</td>
                            <td class="py-1.5 text-right">{{ taka(b.netRevenue) }}</td>
                          </tr>
                        }
                      </tbody>
                    </table>
                  </div>
                </details>
              </section>

              <section class="grid gap-6 lg:grid-cols-2">
                <!-- Sales breakdown -->
                <article class="rounded-xl border border-gray-200 bg-white p-5">
                  <h2 class="font-bold text-gray-900">Sales breakdown</h2>
                  <p class="text-xs text-gray-500 mb-3">Valid orders only. Net = gross − discounts + shipping + surcharges + VAT.</p>
                  <table class="w-full text-sm">
                    <thead>
                      <tr class="border-b border-gray-100 text-xs uppercase text-gray-400">
                        <th class="py-2 text-left">Line</th>
                        <th class="py-2 text-right">{{ r.label }}</th>
                        <th class="py-2 text-right">{{ r.previousLabel }}</th>
                      </tr>
                    </thead>
                    <tbody class="divide-y divide-gray-50">
                      <tr><td class="py-2">Gross sales (merchandise)</td><td class="py-2 text-right">{{ taka(r.current.grossSales) }}</td><td class="py-2 text-right text-gray-500">{{ taka(r.previous.grossSales) }}</td></tr>
                      <tr><td class="py-2">− Discounts &amp; coupons</td><td class="py-2 text-right text-red-600">−{{ taka(r.current.discounts) }}</td><td class="py-2 text-right text-gray-500">−{{ taka(r.previous.discounts) }}</td></tr>
                      <tr><td class="py-2">+ Shipping charged</td><td class="py-2 text-right">{{ taka(r.current.shipping) }}</td><td class="py-2 text-right text-gray-500">{{ taka(r.previous.shipping) }}</td></tr>
                      <tr><td class="py-2">+ Custom-size surcharges</td><td class="py-2 text-right">{{ taka(r.current.surcharges) }}</td><td class="py-2 text-right text-gray-500">{{ taka(r.previous.surcharges) }}</td></tr>
                      <tr><td class="py-2">+ VAT collected</td><td class="py-2 text-right">{{ taka(r.current.vat) }}</td><td class="py-2 text-right text-gray-500">{{ taka(r.previous.vat) }}</td></tr>
                      <tr class="font-bold border-t border-gray-200"><td class="py-2">Net revenue</td><td class="py-2 text-right">{{ taka(r.current.netRevenue) }}</td><td class="py-2 text-right text-gray-500">{{ taka(r.previous.netRevenue) }}</td></tr>
                      <tr><td class="py-2 text-gray-500">of which already delivered</td><td class="py-2 text-right text-emerald-700">{{ taka(r.current.deliveredRevenue) }}</td><td class="py-2 text-right text-gray-500">{{ taka(r.previous.deliveredRevenue) }}</td></tr>
                      <tr><td class="py-2 text-gray-500">Average order value</td><td class="py-2 text-right">{{ taka(r.current.averageOrderValue, 2) }}</td><td class="py-2 text-right text-gray-500">{{ taka(r.previous.averageOrderValue, 2) }}</td></tr>
                      <tr><td class="py-2 text-gray-500">Units sold</td><td class="py-2 text-right">{{ num(r.current.unitsSold) }}</td><td class="py-2 text-right text-gray-500">{{ num(r.previous.unitsSold) }}</td></tr>
                    </tbody>
                  </table>
                </article>

                <!-- Order funnel -->
                <article class="rounded-xl border border-gray-200 bg-white p-5">
                  <h2 class="font-bold text-gray-900">Orders</h2>
                  <p class="text-xs text-gray-500 mb-3">By order date. Cancelled and returned value is not revenue.</p>
                  <table class="w-full text-sm">
                    <thead>
                      <tr class="border-b border-gray-100 text-xs uppercase text-gray-400">
                        <th class="py-2 text-left">Status</th>
                        <th class="py-2 text-right">Orders</th>
                        <th class="py-2 text-right">Value</th>
                        <th class="py-2 text-right">Share</th>
                      </tr>
                    </thead>
                    <tbody class="divide-y divide-gray-50">
                      <tr class="font-semibold"><td class="py-2">Placed</td><td class="py-2 text-right">{{ num(r.current.placedOrders) }}</td><td class="py-2 text-right">{{ taka(r.current.netRevenue + r.current.cancelledValue + r.current.returnedValue) }}</td><td class="py-2 text-right">100%</td></tr>
                      @for (s of statusRows(); track s.status) {
                        <tr>
                          <td class="py-2 pl-3 flex items-center gap-2">
                            <span class="w-2 h-2 rounded-full" [class]="statusDot(s.status)"></span>{{ statusLabel(s.status) }}
                          </td>
                          <td class="py-2 text-right">{{ num(s.count) }}</td>
                          <td class="py-2 text-right" [class.text-red-600]="s.status === 'CANCELLED' || s.status === 'RETURNED'">{{ taka(s.value) }}</td>
                          <td class="py-2 text-right text-gray-500">{{ share(s.count, r.current.placedOrders) }}</td>
                        </tr>
                      }
                    </tbody>
                  </table>
                  <div class="mt-4 grid grid-cols-3 gap-2 text-center text-xs">
                    <div class="rounded-lg bg-emerald-50 p-2"><p class="text-gray-500">Delivery rate</p><p class="text-lg font-bold text-emerald-700">{{ r.current.deliveryRate }}%</p></div>
                    <div class="rounded-lg bg-red-50 p-2"><p class="text-gray-500">Cancellation</p><p class="text-lg font-bold text-red-600">{{ r.current.cancellationRate }}%</p></div>
                    <div class="rounded-lg bg-amber-50 p-2"><p class="text-gray-500">Returns</p><p class="text-lg font-bold text-amber-700">{{ r.current.returnRate }}%</p></div>
                  </div>
                  <p class="mt-3 text-xs text-gray-500">
                    Happened in this period (by event date): <strong>{{ num(r.current.deliveredInPeriodOrders) }}</strong> deliveries worth {{ taka(r.current.deliveredInPeriodRevenue) }},
                    <strong>{{ num(r.current.cancelledInPeriodOrders) }}</strong> cancellations worth {{ taka(r.current.cancelledInPeriodValue) }}.
                  </p>
                </article>
              </section>

              <section class="grid gap-6 lg:grid-cols-2">
                <!-- Payment split -->
                <article class="rounded-xl border border-gray-200 bg-white p-5">
                  <h2 class="font-bold text-gray-900">Payment channels</h2>
                  <p class="text-xs text-gray-500 mb-3">Valid orders. For cash on delivery, "to collect" is money still with the courier or customer.</p>
                  <table class="w-full text-sm">
                    <thead>
                      <tr class="border-b border-gray-100 text-xs uppercase text-gray-400">
                        <th class="py-2 text-left">Method</th>
                        <th class="py-2 text-right">Orders</th>
                        <th class="py-2 text-right">Revenue</th>
                        <th class="py-2 text-right">Collected</th>
                        <th class="py-2 text-right">To collect</th>
                      </tr>
                    </thead>
                    <tbody class="divide-y divide-gray-50">
                      @for (p of r.byPayment; track p.method) {
                        <tr>
                          <td class="py-2">{{ paymentLabel(p.method) }} <span class="text-xs text-gray-400">{{ share(p.revenue, r.current.netRevenue) }}</span></td>
                          <td class="py-2 text-right">{{ num(p.orders) }}</td>
                          <td class="py-2 text-right">{{ taka(p.revenue) }}</td>
                          <td class="py-2 text-right text-emerald-700">{{ taka(p.deliveredRevenue) }}</td>
                          <td class="py-2 text-right" [class.text-amber-700]="p.undeliveredValue > 0">{{ taka(p.undeliveredValue) }}</td>
                        </tr>
                      } @empty {
                        <tr><td colspan="5" class="py-3 text-center text-gray-400">No orders in this period.</td></tr>
                      }
                    </tbody>
                  </table>
                  <p class="mt-3 text-xs text-gray-500">Cash on delivery still to collect: <strong class="text-amber-700">{{ taka(codOutstanding()) }}</strong></p>
                </article>

                <!-- Customers -->
                <article class="rounded-xl border border-gray-200 bg-white p-5">
                  <h2 class="font-bold text-gray-900">Customers</h2>
                  <p class="text-xs text-gray-500 mb-3">A customer is a registered account, or a guest identified by email or phone.</p>
                  <div class="grid grid-cols-2 gap-3">
                    <div class="rounded-lg border border-gray-100 p-3">
                      <p class="text-xs text-gray-500">Customers who ordered</p>
                      <p class="text-2xl font-bold text-gray-900">{{ num(r.current.uniqueCustomers) }}</p>
                      <p class="text-xs text-gray-400">{{ num(r.previous.uniqueCustomers) }} in {{ r.previousLabel }}</p>
                    </div>
                    <div class="rounded-lg border border-gray-100 p-3">
                      <p class="text-xs text-gray-500">First-time customers</p>
                      <p class="text-2xl font-bold text-gray-900">{{ num(r.current.newCustomers) }}</p>
                      <p class="text-xs text-gray-400">{{ num(r.previous.newCustomers) }} in {{ r.previousLabel }}</p>
                    </div>
                    <div class="rounded-lg border border-gray-100 p-3">
                      <p class="text-xs text-gray-500">Orders per customer</p>
                      <p class="text-2xl font-bold text-gray-900">{{ ratio(r.current.orders, r.current.uniqueCustomers) }}</p>
                    </div>
                    <div class="rounded-lg border border-gray-100 p-3">
                      <p class="text-xs text-gray-500">Revenue per customer</p>
                      <p class="text-2xl font-bold text-gray-900">{{ r.current.uniqueCustomers ? taka(r.current.netRevenue / r.current.uniqueCustomers) : '—' }}</p>
                    </div>
                  </div>
                </article>
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
                        <th class="py-2 text-right">Orders</th>
                        <th class="py-2 text-right">Sales value</th>
                      </tr>
                    </thead>
                    <tbody class="divide-y divide-gray-50">
                      @for (p of r.topProducts; track p.productId; let i = $index) {
                        <tr>
                          <td class="py-2">
                            <div class="flex items-center gap-2">
                              <span class="w-5 text-xs text-gray-400">{{ i + 1 }}.</span>
                              @if (p.imageUrl) { <img [src]="p.imageUrl" [alt]="p.productName" class="w-8 h-8 rounded object-cover bg-gray-100" /> }
                              <span class="truncate max-w-[220px]">{{ p.productName }}</span>
                            </div>
                          </td>
                          <td class="py-2 text-right">{{ num(p.unitsSold) }}</td>
                          <td class="py-2 text-right">{{ num(p.orderCount) }}</td>
                          <td class="py-2 text-right">{{ taka(p.revenue) }}</td>
                        </tr>
                      } @empty {
                        <tr><td colspan="4" class="py-3 text-center text-gray-400">No sales in this period.</td></tr>
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
                        <th class="py-2 text-right">Orders</th>
                        <th class="py-2 text-right">Sales value</th>
                      </tr>
                    </thead>
                    <tbody class="divide-y divide-gray-50">
                      @for (c of r.topCategories; track c.categoryId; let i = $index) {
                        <tr>
                          <td class="py-2"><span class="w-5 inline-block text-xs text-gray-400">{{ i + 1 }}.</span>{{ c.categoryName }}</td>
                          <td class="py-2 text-right">{{ num(c.unitsSold) }}</td>
                          <td class="py-2 text-right">{{ num(c.orderCount) }}</td>
                          <td class="py-2 text-right">{{ taka(c.revenue) }}</td>
                        </tr>
                      } @empty {
                        <tr><td colspan="4" class="py-3 text-center text-gray-400">No sales in this period.</td></tr>
                      }
                    </tbody>
                  </table>
                </article>
              </section>

              <!-- Definitions -->
              <section class="rounded-xl border border-dashed border-gray-200 bg-white p-5 text-xs text-gray-500 space-y-1">
                <p class="font-semibold text-gray-700">How these numbers are calculated</p>
                <p>• Periods are Bangladesh time (Asia/Dhaka). A day runs midnight to midnight; a week runs Sunday to Saturday; the fiscal year runs 1 July to 30 June.</p>
                <p>• Orders belong to the period in which they were placed. Cancelled and returned orders are counted as orders but never as revenue.</p>
                <p>• Net revenue is what the customer pays: merchandise − discounts + shipping + custom-size surcharges + VAT. "Delivered" revenue is the part already fulfilled.</p>
                <p>• Sales value in the product and category tables is quantity × price at order (before order-level discounts), so it does not add up to net revenue.</p>
                <p>• Generated {{ r.generatedAt.replace('T', ' ').slice(0, 19) }} {{ r.zone }}.</p>
              </section>
            }
          </div>
        </main>
      </div>
    </div>
  `,
})
export class ReportsComponent implements OnInit {
  private reportService = inject(ReportService);

  readonly tabs: Tab[] = [
    { type: 'DAY', label: 'Daily', hint: 'One calendar day, midnight to midnight Dhaka time' },
    { type: 'WEEK', label: 'Weekly', hint: 'Sunday to Saturday' },
    { type: 'MONTH', label: 'Monthly', hint: 'Calendar month' },
    { type: 'YEAR', label: 'Yearly', hint: 'Calendar year, January to December' },
    { type: 'FISCAL_YEAR', label: 'Fiscal year', hint: '1 July to 30 June' },
  ];

  readonly today = businessToday();
  readonly chartW = 800;
  readonly chartH = 220;
  readonly gridLines = [44, 88, 132, 176];

  type = signal<PeriodType>('DAY');
  anchor = signal<string>(this.today);
  report = signal<PeriodReport | null>(null);
  loading = signal(false);
  errorMessage = signal('');

  tabLabel = computed(() => this.tabs.find(t => t.type === this.type())?.label ?? '');

  kpis = computed<Kpi[]>(() => {
    const r = this.report();
    if (!r) return [];
    const c = r.current, p = r.previous;
    const mk = (key: keyof PeriodMetrics, label: string, money: boolean, inverse = false, hint?: string): Kpi => ({
      key, label, money, inverse, hint,
      current: Number(c[key]), previous: Number(p[key]),
      delta: percentChange(Number(c[key]), Number(p[key])),
    });
    return [
      mk('netRevenue', 'Net revenue', true, false, `${c.orders.toLocaleString()} valid orders`),
      mk('orders', 'Valid orders', false, false, `${c.placedOrders.toLocaleString()} placed incl. cancelled`),
      mk('averageOrderValue', 'Average order value', true),
      mk('deliveredRevenue', 'Delivered revenue', true, false, `${c.deliveredOrders.toLocaleString()} delivered`),
      mk('cancelledOrders', 'Cancelled orders', false, true, `${formatTaka(c.cancelledValue)} lost · ${c.cancellationRate}% rate`),
      mk('unitsSold', 'Units sold', false),
      mk('uniqueCustomers', 'Customers', false, false, `${c.newCustomers.toLocaleString()} first-time`),
      mk('discounts', 'Discounts given', true, true),
    ];
  });

  maxBucket = computed(() => Math.max(0, ...(this.report()?.breakdown ?? []).map(b => b.netRevenue)));

  bars = computed<ChartBar[]>(() => {
    const r = this.report();
    if (!r) return [];
    const n = r.breakdown.length;
    if (n === 0) return [];
    const max = this.maxBucket() || 1;
    const slot = this.chartW / n;
    const gap = Math.min(6, slot * 0.25);
    const top = 12, bottom = 18;
    const labelEvery = n > 24 ? 5 : n > 12 ? 3 : 1;
    return r.breakdown.map((b, i) => {
      const h = Math.round(((this.chartH - top - bottom) * b.netRevenue) / max);
      return {
        x: i * slot + gap / 2, w: slot - gap,
        y: this.chartH - bottom - h, h: Math.max(h, b.netRevenue > 0 ? 2 : 0),
        label: b.label, showLabel: i % labelEvery === 0,
        orders: b.orders, cancelled: b.cancelledOrders, revenue: b.netRevenue,
      };
    });
  });

  statusRows = computed(() => {
    const rows = this.report()?.byStatus ?? [];
    return [...rows].sort((a, b) => STATUS_ORDER.indexOf(a.status) - STATUS_ORDER.indexOf(b.status));
  });

  codOutstanding = computed(() =>
    (this.report()?.byPayment ?? [])
      .filter(p => p.method === 'CASH' || p.method === 'COD')
      .reduce((s, p) => s + p.undeliveredValue, 0));

  canStepForward = computed(() => {
    const r = this.report();
    return r ? r.end < this.today : this.anchor() < this.today;
  });

  ngOnInit(): void {
    this.load();
  }

  setType(type: PeriodType): void {
    if (this.type() === type) return;
    this.type.set(type);
    this.load();
  }

  setAnchor(date: string): void {
    if (!date || date === this.anchor()) return;
    this.anchor.set(date > this.today ? this.today : date);
    this.load();
  }

  /** Move one period back or forward, anchored on the period's first day. */
  step(direction: 1 | -1): void {
    const base = this.report()?.start ?? this.anchor();
    let next: string;
    switch (this.type()) {
      case 'DAY': next = addDays(base, direction); break;
      case 'WEEK': next = addDays(base, 7 * direction); break;
      case 'MONTH': next = addMonths(base, direction); break;
      default: next = addYears(base, direction); break;
    }
    // Moving forward into the current period should land on today, so the
    // date box never shows a future date.
    if (next > this.today) next = this.today;
    this.setAnchor(next);
  }

  load(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    const type = this.type(), anchor = this.anchor();
    this.reportService.period(type, anchor).subscribe({
      next: (r) => {
        // Ignore a stale response if the user has already moved on.
        if (this.type() !== type || this.anchor() !== anchor) return;
        this.report.set(r);
        this.loading.set(false);
      },
      error: (err) => { this.errorMessage.set(parseApiError(err)); this.loading.set(false); },
    });
  }

  // ── formatting helpers used by the template ───────────────────────────────

  taka(v: number, digits = 0): string { return formatTaka(v, digits); }
  takaCompact(v: number): string { return formatTakaCompact(v); }
  num(v: number): string { return Number(v ?? 0).toLocaleString('en-IN'); }
  abs(v: number): number { return Math.abs(v); }
  formatDate(iso: string): string { return formatDate(iso); }

  share(part: number, whole: number): string {
    if (!whole) return '—';
    return (Math.round((part / whole) * 1000) / 10) + '%';
  }

  ratio(a: number, b: number): string {
    if (!b) return '—';
    return (Math.round((a / b) * 100) / 100).toFixed(2);
  }

  deltaClass(delta: number, inverse: boolean): string {
    if (delta === 0) return 'text-gray-500';
    const good = inverse ? delta < 0 : delta > 0;
    return good ? 'text-emerald-600' : 'text-red-600';
  }

  paymentLabel(method: string): string {
    return PAYMENT_LABELS[method] ?? method;
  }

  statusLabel(status: string): string {
    return status.toLowerCase().replace(/_/g, ' ').replace(/^\w/, c => c.toUpperCase());
  }

  statusDot(status: string): string {
    switch (status) {
      case 'DELIVERED': return 'bg-emerald-500';
      case 'CANCELLED': return 'bg-red-500';
      case 'RETURNED': return 'bg-amber-500';
      case 'PLACED': return 'bg-blue-400';
      default: return 'bg-violet-400';
    }
  }

  print(): void {
    window.print();
  }

  /** Every table on the page as one CSV, for Excel / the accountant. */
  exportCsv(): void {
    const r = this.report();
    if (!r) return;
    const rows: (string | number)[][] = [];
    rows.push(['Erezer business report', r.label, `${r.start} to ${r.end}`, r.zone, `generated ${r.generatedAt}`]);
    rows.push([]);
    rows.push(['Metric', r.label, r.previousLabel]);
    const m: [string, keyof PeriodMetrics][] = [
      ['Placed orders', 'placedOrders'], ['Valid orders', 'orders'], ['Delivered orders', 'deliveredOrders'],
      ['Cancelled orders', 'cancelledOrders'], ['Returned orders', 'returnedOrders'], ['Pending orders', 'pendingOrders'],
      ['In-progress orders', 'inProgressOrders'], ['Units sold', 'unitsSold'],
      ['Gross sales', 'grossSales'], ['Discounts', 'discounts'], ['Shipping', 'shipping'], ['Surcharges', 'surcharges'],
      ['VAT', 'vat'], ['Net revenue', 'netRevenue'], ['Delivered revenue', 'deliveredRevenue'],
      ['Average order value', 'averageOrderValue'], ['Cancelled value', 'cancelledValue'], ['Returned value', 'returnedValue'],
      ['Customers', 'uniqueCustomers'], ['New customers', 'newCustomers'],
      ['Delivery rate %', 'deliveryRate'], ['Cancellation rate %', 'cancellationRate'], ['Return rate %', 'returnRate'],
      ['Deliveries in period', 'deliveredInPeriodOrders'], ['Delivered value in period', 'deliveredInPeriodRevenue'],
      ['Cancellations in period', 'cancelledInPeriodOrders'], ['Cancelled value in period', 'cancelledInPeriodValue'],
    ];
    for (const [label, key] of m) rows.push([label, r.current[key], r.previous[key]]);
    rows.push([]);
    rows.push([`Breakdown by ${r.bucketUnit}`, 'Placed', 'Valid', 'Cancelled', 'Net revenue']);
    for (const b of r.breakdown) rows.push([b.bucketStart, b.placedOrders, b.orders, b.cancelledOrders, b.netRevenue]);
    rows.push([]);
    rows.push(['Status', 'Orders', 'Value']);
    for (const s of this.statusRows()) rows.push([s.status, s.count, s.value]);
    rows.push([]);
    rows.push(['Payment method', 'Orders', 'Revenue', 'Delivered orders', 'Delivered revenue', 'Undelivered orders', 'To collect', 'Cancelled orders']);
    for (const p of r.byPayment) rows.push([this.paymentLabel(p.method), p.orders, p.revenue, p.deliveredOrders, p.deliveredRevenue, p.undeliveredOrders, p.undeliveredValue, p.cancelledOrders]);
    rows.push([]);
    rows.push(['Top product', 'Units', 'Orders', 'Sales value']);
    for (const p of r.topProducts) rows.push([p.productName, p.unitsSold, p.orderCount, p.revenue]);
    rows.push([]);
    rows.push(['Top category', 'Units', 'Orders', 'Sales value']);
    for (const c of r.topCategories) rows.push([c.categoryName, c.unitsSold, c.orderCount, c.revenue]);

    const csv = rows.map(row => row.map(cell => {
      const s = String(cell ?? '');
      return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
    }).join(',')).join('\r\n');
    const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `erezer-${r.type.toLowerCase()}-report-${r.start}.csv`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  // Referenced by the template for type-narrowing of payment rows.
  protected trackPayment(_: number, p: PaymentSplit): string { return p.method; }
  protected trackBucket(_: number, b: ReportBucket): string { return b.bucketStart; }
}
