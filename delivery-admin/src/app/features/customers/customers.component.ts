import { Component, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { HiddenMoneyComponent } from '../../shared/hidden-money/hidden-money.component';
import { PagerComponent } from '../../shared/pager/pager.component';
import { CustomerLifetimeValue, ReportService } from '../../core/services/report.service';

/**
 * Every customer: each account (with or without orders) and each guest shopper,
 * known by the email on their orders. Ranked by lifetime revenue. The server
 * searches name, email and phone and sends one page at a time.
 */
@Component({
  selector: 'app-customers',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, DecimalPipe, FormsModule, SidebarComponent, HiddenMoneyComponent, PagerComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between gap-4 flex-shrink-0">
          <div>
            <h1 class="text-lg font-bold text-gray-900">Customers</h1>
            <p class="text-xs text-gray-400">Accounts and guest shoppers, ranked by lifetime revenue (cancelled & returned orders excluded)</p>
          </div>
          <div class="flex items-center gap-4">
            <input type="search" [ngModel]="searchQuery()" (ngModelChange)="onSearchChange($event)"
              placeholder="Search by name, email or phone…" aria-label="Search customers"
              class="w-64 rounded-lg border border-gray-200 px-3 py-1.5 text-sm outline-none focus:ring-2 focus:ring-blue-300" />
            @if (total() !== null) {
              <p class="text-sm text-gray-500 whitespace-nowrap" data-testid="customer-total">
                {{ total() | number }} {{ searchQuery().trim() ? 'match(es)' : 'customer(s)' }}
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
                @for (c of customers(); track rowKey(c)) {
                  <tr [class.opacity-60]="loading()">
                    <td class="px-4 py-2.5">
                      <div class="flex items-center gap-2">
                        <p class="font-medium text-gray-800">{{ c.customerName || '—' }}</p>
                        @if (c.guest) {
                          <span class="rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber-700"
                            title="Ordered without an account">Guest</span>
                        }
                      </div>
                      <p class="text-xs text-gray-500">
                        {{ c.email || 'No email' }}@if (c.phone) { · {{ c.phone }} }
                      </p>
                      @if (!c.guest && c.joinedAt) {
                        <p class="text-[11px] text-gray-400">Account since {{ c.joinedAt | date: 'mediumDate' }}</p>
                      }
                    </td>
                    <td class="px-4 py-2.5 text-right" [class.text-gray-400]="c.orderCount === 0">{{ c.orderCount | number }}</td>
                    <td class="px-4 py-2.5 text-right font-semibold">
                      @if (c.lifetimeRevenue == null) { <app-hidden-money /> }
                      @else { {{ c.lifetimeRevenue | currency: 'BDT' : 'symbol-narrow' : '1.0-0' }} }
                    </td>
                    <td class="px-4 py-2.5 text-right text-gray-700">
                      @if (c.averageOrderValue == null) { <app-hidden-money /> }
                      @else { {{ c.averageOrderValue | currency: 'BDT' : 'symbol-narrow' : '1.0-0' }} }
                    </td>
                    <td class="px-4 py-2.5 text-xs text-gray-500">{{ c.firstOrderAt ? (c.firstOrderAt | date: 'mediumDate') : '—' }}</td>
                    <td class="px-4 py-2.5 text-xs text-gray-500">{{ c.lastOrderAt ? (c.lastOrderAt | date: 'mediumDate') : '—' }}</td>
                  </tr>
                } @empty {
                  @if (!loading()) {
                    <tr><td colspan="6" class="px-4 py-6 text-center text-gray-400">
                      {{ error() || (searchQuery().trim() ? 'No customers match your search.' : 'No customers yet.') }}
                    </td></tr>
                  }
                }
              </tbody>
            </table>
            <div class="border-t border-gray-100 bg-gray-50">
              <app-pager [page]="page()" [size]="pageSize" [total]="total() ?? 0" [disabled]="loading()" (pageChange)="loadPage($event)" />
            </div>
          </section>
        </main>
      </div>
    </div>
  `,
})
export class CustomersComponent implements OnInit, OnDestroy {
  private readonly api = inject(ReportService);

  readonly pageSize = 25;
  readonly customers = signal<CustomerLifetimeValue[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  /** Zero-based page on screen. */
  readonly page = signal(0);
  readonly total = signal<number | null>(null);
  readonly searchQuery = signal('');
  private readonly searchSubject = new Subject<string>();
  /** Numbers each request, so an answer that arrives after a newer one is ignored. */
  private latestRequest = 0;

  ngOnInit(): void {
    this.loadPage(0);
    this.searchSubject.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => this.loadPage(0));
  }

  ngOnDestroy(): void {
    this.searchSubject.complete();
  }

  protected onSearchChange(query: string): void {
    this.searchQuery.set(query);
    this.searchSubject.next(query);
  }

  /** Accounts by id; guests by email, since they have no account. */
  protected rowKey(c: CustomerLifetimeValue): string {
    return c.userId ?? 'guest:' + c.email;
  }

  /** One page (zero-based) for the current search. */
  loadPage(page: number): void {
    const request = ++this.latestRequest;
    this.loading.set(true);
    this.error.set('');
    this.api.customers(this.searchQuery().trim(), page, this.pageSize).subscribe({
      next: (res) => {
        if (request !== this.latestRequest) return;
        // Asked for a page past the end (the list shrank): show the last one.
        if (res.content.length === 0 && page > 0 && res.totalPages > 0) {
          this.loadPage(res.totalPages - 1);
          return;
        }
        this.customers.set(res.content);
        this.total.set(res.totalElements);
        this.page.set(res.number);
        this.loading.set(false);
      },
      error: () => {
        if (request !== this.latestRequest) return;
        this.customers.set([]);
        this.total.set(null);
        this.error.set('Could not load customers. Please try again.');
        this.loading.set(false);
      },
    });
  }
}
