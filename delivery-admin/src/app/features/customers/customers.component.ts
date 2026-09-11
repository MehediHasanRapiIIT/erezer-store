import { Component, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, catchError, debounceTime, distinctUntilChanged, of } from 'rxjs';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { HiddenMoneyComponent } from '../../shared/hidden-money/hidden-money.component';
import { CustomerLifetimeValue, ReportService } from '../../core/services/report.service';

/** The money fields arrive as null without the "See money totals" (finance.revenue) permission. */
type CustomerRow = Omit<CustomerLifetimeValue, 'lifetimeRevenue' | 'averageOrderValue'> & {
  lifetimeRevenue: number | null;
  averageOrderValue: number | null;
};

/**
 * Purchasing customers, ranked by lifetime revenue. The server searches name,
 * email and phone across every customer; "Load more" fetches the next 50.
 */
@Component({
  selector: 'app-customers',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, DecimalPipe, FormsModule, SidebarComponent, HiddenMoneyComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between gap-4 flex-shrink-0">
          <div>
            <h1 class="text-lg font-bold text-gray-900">Customers</h1>
            <p class="text-xs text-gray-400">Ranked by lifetime revenue (cancelled & returned orders excluded)</p>
          </div>
          <div class="flex items-center gap-4">
            <input type="search" [ngModel]="searchQuery()" (ngModelChange)="onSearchChange($event)"
              placeholder="Search by name, email or phone…" aria-label="Search customers"
              class="w-64 rounded-lg border border-gray-200 px-3 py-1.5 text-sm outline-none focus:ring-2 focus:ring-blue-300" />
            @if (totalCount() !== null) {
              <p class="text-sm text-gray-500 whitespace-nowrap">
                {{ totalCount() | number }} {{ searchQuery().trim() ? 'match(es)' : 'purchasing customer(s)' }}
              </p>
            }
          </div>
        </header>

        <main class="flex-1 overflow-y-auto p-6">
          <section class="max-w-6xl mx-auto rounded-xl border border-gray-200 bg-white overflow-hidden">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-gray-100 bg-gray-50 text-xs uppercase text-gray-400">
                  <th class="px-4 py-2.5 text-left">Customer</th>
                  <th class="px-4 py-2.5 text-right">Orders</th>
                  <th class="px-4 py-2.5 text-right">Lifetime revenue</th>
                  <th class="px-4 py-2.5 text-right">AOV</th>
                  <th class="px-4 py-2.5 text-left">First order</th>
                  <th class="px-4 py-2.5 text-left">Last order</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-50">
                @if (loading() && customers().length === 0) {
                  <tr><td colspan="6" class="px-4 py-6 text-center text-gray-400">Loading…</td></tr>
                }
                @for (c of customers(); track c.userId) {
                  <tr>
                    <td class="px-4 py-2.5">
                      <p class="font-medium text-gray-800">{{ c.customerName || '—' }}</p>
                      <p class="text-xs text-gray-500">{{ c.email }}</p>
                    </td>
                    <td class="px-4 py-2.5 text-right">{{ c.orderCount | number }}</td>
                    <td class="px-4 py-2.5 text-right font-semibold">
                      @if (c.lifetimeRevenue == null) { <app-hidden-money /> }
                      @else { {{ c.lifetimeRevenue | currency: 'BDT' : 'symbol-narrow' : '1.0-0' }} }
                    </td>
                    <td class="px-4 py-2.5 text-right text-gray-700">
                      @if (c.averageOrderValue == null) { <app-hidden-money /> }
                      @else { {{ c.averageOrderValue | currency: 'BDT' : 'symbol-narrow' : '1.0-0' }} }
                    </td>
                    <td class="px-4 py-2.5 text-xs text-gray-500">{{ c.firstOrderAt | date: 'mediumDate' }}</td>
                    <td class="px-4 py-2.5 text-xs text-gray-500">{{ c.lastOrderAt  | date: 'mediumDate' }}</td>
                  </tr>
                } @empty {
                  @if (!loading()) {
                    <tr><td colspan="6" class="px-4 py-6 text-center text-gray-400">
                      {{ searchQuery().trim() ? 'No customers match your search.' : 'No purchasing customers yet.' }}
                    </td></tr>
                  }
                }
              </tbody>
            </table>
            @if (customers().length > 0) {
              <div class="flex items-center justify-between border-t border-gray-100 bg-gray-50 px-4 py-3 text-sm">
                <span class="text-xs text-gray-500 tabular-nums">
                  Showing {{ customers().length | number }}@if (totalCount() !== null) { of {{ totalCount() | number }}}
                </span>
                @if (canLoadMore()) {
                  <button type="button" (click)="loadMore()" [disabled]="loading()" class="text-blue-600 underline disabled:opacity-50">
                    {{ loading() ? 'Loading…' : 'Load more' }}
                  </button>
                }
              </div>
            }
          </section>
        </main>
      </div>
    </div>
  `,
})
export class CustomersComponent implements OnInit, OnDestroy {
  private readonly api = inject(ReportService);

  readonly pageSize = 50;
  readonly customers  = signal<CustomerRow[]>([]);
  readonly loading    = signal(false);
  readonly totalCount = signal<number | null>(null);
  readonly searchQuery = signal('');
  private readonly searchSubject = new Subject<string>();
  /** Set when the last page came back short, so there is nothing more to load. */
  private readonly exhausted = signal(false);
  /** Numbers each request, so an answer that arrives after a newer one is ignored. */
  private latestRequest = 0;

  ngOnInit(): void {
    this.reload();
    this.searchSubject.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => this.reload());
  }

  ngOnDestroy(): void {
    this.searchSubject.complete();
  }

  protected onSearchChange(query: string): void {
    this.searchQuery.set(query);
    this.searchSubject.next(query);
  }

  protected canLoadMore(): boolean {
    const total = this.totalCount();
    return !this.exhausted() && (total === null || this.customers().length < total);
  }

  /** The first page and the count, for the current search. */
  protected reload(): void {
    const request = ++this.latestRequest;
    const q = this.searchQuery().trim();
    this.loading.set(true);
    this.exhausted.set(false);
    this.api.searchCustomers(q, this.pageSize, 0).pipe(catchError(() => of([] as CustomerLifetimeValue[])))
      .subscribe((list) => {
        if (request !== this.latestRequest) return;
        this.customers.set(list);
        this.exhausted.set(list.length < this.pageSize);
        this.loading.set(false);
      });
    this.api.customerCount(q).pipe(catchError(() => of(null))).subscribe((n) => {
      if (request === this.latestRequest) this.totalCount.set(n);
    });
  }

  protected loadMore(): void {
    const request = this.latestRequest;
    const offset = this.customers().length;
    this.loading.set(true);
    this.api.searchCustomers(this.searchQuery().trim(), this.pageSize, offset)
      .pipe(catchError(() => of([] as CustomerLifetimeValue[])))
      .subscribe((list) => {
        if (request !== this.latestRequest) return;
        this.customers.update((existing) => [...existing, ...list]);
        this.exhausted.set(list.length < this.pageSize);
        this.loading.set(false);
      });
  }
}
