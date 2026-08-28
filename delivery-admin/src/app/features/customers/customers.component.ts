import { Component, inject, OnInit, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { catchError, of } from 'rxjs';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { CustomerLifetimeValue, ReportService } from '../../core/services/report.service';

@Component({
  selector: 'app-customers',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, DecimalPipe, SidebarComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between flex-shrink-0">
          <div>
            <h1 class="text-lg font-bold text-gray-900">Customers</h1>
            <p class="text-xs text-gray-400">Ranked by lifetime revenue (cancelled & returned orders excluded)</p>
          </div>
          @if (totalCount() !== null) {
            <p class="text-sm text-gray-500">{{ totalCount() | number }} purchasing customer(s)</p>
          }
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
                @if (loading()) {
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
                      {{ c.lifetimeRevenue | currency: 'BDT' : 'symbol-narrow' : '1.0-0' }}
                    </td>
                    <td class="px-4 py-2.5 text-right text-gray-700">
                      {{ c.averageOrderValue | currency: 'BDT' : 'symbol-narrow' : '1.0-0' }}
                    </td>
                    <td class="px-4 py-2.5 text-xs text-gray-500">{{ c.firstOrderAt | date: 'mediumDate' }}</td>
                    <td class="px-4 py-2.5 text-xs text-gray-500">{{ c.lastOrderAt  | date: 'mediumDate' }}</td>
                  </tr>
                } @empty {
                  @if (!loading()) {
                    <tr><td colspan="6" class="px-4 py-6 text-center text-gray-400">No purchasing customers yet.</td></tr>
                  }
                }
              </tbody>
            </table>
            @if (customers().length === pageSize) {
              <div class="border-t border-gray-100 bg-gray-50 px-4 py-3 text-right text-sm">
                <button (click)="loadMore()" class="text-blue-600 underline">Load more</button>
              </div>
            }
          </section>
        </main>
      </div>
    </div>
  `,
})
export class CustomersComponent implements OnInit {
  private readonly api = inject(ReportService);

  readonly pageSize = 50;
  readonly customers  = signal<CustomerLifetimeValue[]>([]);
  readonly loading    = signal(false);
  readonly totalCount = signal<number | null>(null);

  ngOnInit(): void {
    this.loadInitial();
    this.api.customerCount().pipe(catchError(() => of(null))).subscribe((n) => {
      if (n !== null) this.totalCount.set(n);
    });
  }

  protected loadInitial(): void {
    this.loading.set(true);
    this.api.customers(this.pageSize, 0).pipe(catchError(() => of([] as CustomerLifetimeValue[])))
      .subscribe((list) => {
        this.customers.set(list);
        this.loading.set(false);
      });
  }

  protected loadMore(): void {
    const offset = this.customers().length;
    this.loading.set(true);
    this.api.customers(this.pageSize, offset).pipe(catchError(() => of([] as CustomerLifetimeValue[])))
      .subscribe((list) => {
        this.customers.update((existing) => [...existing, ...list]);
        this.loading.set(false);
      });
  }
}
