import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { EcommerceStore } from '../core/store/ecommerce.store';

@Component({
  standalone: true,
  imports: [CurrencyPipe],
  template: `
    <section class="space-y-6">
      <header>
        <h1 class="text-3xl font-semibold">Admin Dashboard</h1>
        <p class="text-neutral-600 dark:text-neutral-300">Snapshot of catalog and order performance.</p>
      </header>

      <div class="grid gap-4 sm:grid-cols-3">
        <article class="rounded-2xl border border-neutral-200 bg-white p-4 dark:border-neutral-700 dark:bg-neutral-900">
          <p class="text-sm text-neutral-500 dark:text-neutral-400">Products</p>
          <p class="text-2xl font-semibold">{{ store.products().length }}</p>
        </article>
        <article class="rounded-2xl border border-neutral-200 bg-white p-4 dark:border-neutral-700 dark:bg-neutral-900">
          <p class="text-sm text-neutral-500 dark:text-neutral-400">Orders</p>
          <p class="text-2xl font-semibold">{{ store.orders().length }}</p>
        </article>
        <article class="rounded-2xl border border-neutral-200 bg-white p-4 dark:border-neutral-700 dark:bg-neutral-900">
          <p class="text-sm text-neutral-500 dark:text-neutral-400">Revenue</p>
          <p class="text-2xl font-semibold">{{ totalRevenue() | currency:'BDT':'৳' }}</p>
        </article>
      </div>

      <article class="rounded-2xl border border-neutral-200 bg-white p-5 dark:border-neutral-700 dark:bg-neutral-900">
        <h2 class="mb-3 text-xl font-semibold">Recent Orders</h2>
        @for (order of store.orders(); track order.id) {
          <div class="mb-2 flex justify-between border-b border-neutral-100 pb-2 text-sm last:mb-0 last:border-0">
            <span>{{ order.id }}</span>
            <span>{{ order.total | currency:'BDT':'৳' }}</span>
          </div>
        } @empty {
          <p class="text-sm text-neutral-500 dark:text-neutral-400">No recent orders.</p>
        }
      </article>
    </section>
  `
})
export class AdminPage {
  protected readonly store = inject(EcommerceStore);
  protected readonly totalRevenue = computed(() =>
    this.store.orders().reduce((acc, order) => acc + order.total, 0)
  );
}
