import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ApiService } from '../core/api.service';
import { ApiOrder } from '../core/api.models';
import { AuthService } from '../core/auth.service';
import { EcommerceStore } from '../core/store/ecommerce.store';
import { RevealDirective } from '../core/reveal.directive';

@Component({
  standalone: true,
  imports: [DatePipe, CurrencyPipe, RouterLink, RevealDirective],
  template: `
    <section class="space-y-6">
      <header class="mb-2" appReveal>
        <p class="text-xs font-semibold uppercase tracking-[0.28em] text-neutral-500 dark:text-neutral-400">Order history</p>
        <h1 class="app-section-title mt-2 text-3xl md:text-4xl">My orders</h1>
        <p class="mt-2 text-neutral-600 dark:text-neutral-300">Track payments and delivery from one place.</p>
      </header>

      @if (loading()) {
        <div class="space-y-3">
          @for (s of [0,1,2]; track s) {
            <div class="app-card p-5">
              <div class="flex items-center justify-between">
                <div class="space-y-2">
                  <div class="h-4 w-40 animate-pulse rounded bg-neutral-200 dark:bg-neutral-800"></div>
                  <div class="h-3 w-28 animate-pulse rounded bg-neutral-200 dark:bg-neutral-800"></div>
                </div>
                <div class="h-6 w-24 animate-pulse rounded-full bg-neutral-200 dark:bg-neutral-800"></div>
              </div>
            </div>
          }
        </div>
      } @else if (apiOrders().length === 0 && store.orders().length === 0) {
        <div class="flex flex-col items-center gap-4 py-20 text-center" appReveal>
          <span class="inline-flex h-20 w-20 items-center justify-center rounded-full bg-neutral-100 text-neutral-400 dark:bg-neutral-800">
            <svg class="h-9 w-9" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.4"><path stroke-linecap="round" stroke-linejoin="round" d="M2.25 3h1.386c.51 0 .955.343 1.087.835l.383 1.437M7.5 14.25a3 3 0 00-3 3h15.75m-12.75-3h11.218c1.121-2.3 2.1-4.684 2.924-7.138a60.114 60.114 0 00-16.536-1.84M7.5 14.25L5.106 5.272"/></svg>
          </span>
          <h2 class="app-section-title text-2xl">No orders yet</h2>
          <p class="max-w-sm app-muted">When you place an order it'll show up here so you can track it.</p>
          <a routerLink="/shop" class="btn-primary mt-1 !rounded-full">Start shopping</a>
        </div>
      } @else {
        <div class="space-y-3">
          <!-- API orders (authenticated) -->
          @for (order of apiOrders(); track order.id; let i = $index) {
            <a [routerLink]="['/orders', order.id]"
              class="group block app-card p-5 transition-all duration-300 hover:-translate-y-0.5 hover:shadow-lg hover:shadow-black/5"
              [appReveal]="i">
              <div class="flex flex-wrap items-start justify-between gap-3">
                <div class="flex items-center gap-3">
                  <span class="inline-flex h-11 w-11 items-center justify-center rounded-full" [class]="badgeClass(order.orderStatus)">
                    <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M8.25 18.75a1.5 1.5 0 11-3 0m3 0a1.5 1.5 0 00-3 0m3 0h6m-9 0H3.375a1.125 1.125 0 01-1.125-1.125V14.25m17.25 4.5a1.5 1.5 0 11-3 0m3 0a1.5 1.5 0 00-3 0m3 0h1.125c.621 0 1.129-.504 1.09-1.124a17.902 17.902 0 00-3.213-9.193 2.056 2.056 0 00-1.66-.84H14.25M16.5 18.75h-6V5.25A1.125 1.125 0 009.375 4.125H4.5"/></svg>
                  </span>
                  <div>
                    <p class="font-medium">Order #{{ order.id }}</p>
                    <p class="text-sm text-neutral-500 dark:text-neutral-400">{{ order.createdAt | date: 'medium' }}</p>
                  </div>
                </div>
                <span class="inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold" [class]="badgeClass(order.orderStatus)">
                  {{ statusLabel(order.orderStatus) }}
                </span>
              </div>
              <div class="mt-4 flex items-center justify-between border-t border-neutral-100 pt-3 dark:border-neutral-800">
                <p class="text-sm text-neutral-500 dark:text-neutral-400">Payment · {{ order.paymentMethod }}</p>
                <div class="flex items-center gap-3">
                  <p class="font-semibold tabular-nums">{{ order.totalAmount | currency:'BDT':'৳' }}</p>
                  <span class="inline-flex items-center gap-1 text-sm font-medium text-neutral-600 transition group-hover:gap-2 dark:text-neutral-300">
                    Details
                    <svg class="h-4 w-4 transition-transform group-hover:translate-x-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7"/></svg>
                  </span>
                </div>
              </div>
            </a>
          }

          <!-- Local (guest) orders -->
          @for (order of store.orders(); track order.id; let i = $index) {
            <a [routerLink]="['/orders', order.id]"
              class="group block app-card p-5 transition-all duration-300 hover:-translate-y-0.5 hover:shadow-lg hover:shadow-black/5"
              [appReveal]="i">
              <div class="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p class="font-medium">Order #{{ order.id }}</p>
                  <p class="text-sm text-neutral-500 dark:text-neutral-400">{{ order.createdAt | date: 'medium' }}</p>
                </div>
                <div class="text-right text-sm text-neutral-500 dark:text-neutral-400">
                  <p>Payment: {{ order.payment.status }}</p>
                  <p>Delivery: {{ order.delivery.status }}</p>
                </div>
              </div>
              <div class="mt-3 flex items-center justify-between border-t border-neutral-100 pt-3 dark:border-neutral-800">
                <p class="font-semibold tabular-nums">{{ order.total | currency:'BDT':'৳' }}</p>
                <span class="text-sm font-medium text-neutral-600 underline-offset-4 group-hover:underline dark:text-neutral-300">View details</span>
              </div>
            </a>
          }
        </div>
      }
    </section>
  `
})
export class OrdersPage implements OnInit {
  protected readonly store = inject(EcommerceStore);
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  protected readonly loading   = signal(false);
  protected readonly apiOrders = signal<ApiOrder[]>([]);

  ngOnInit(): void {
    const userId = this.auth.userId();
    if (userId) {
      this.loading.set(true);
      this.api.getOrders(userId).pipe(catchError(() => of([]))).subscribe((orders) => {
        this.apiOrders.set(orders);
        this.loading.set(false);
      });
    }
  }

  protected statusLabel(status: string): string {
    switch (status) {
      case 'PLACED':           return 'Placed';
      case 'PENDING':          return 'Placed';
      case 'ACCEPTED':         return 'Accepted';
      case 'IN_PRODUCTION':    return 'In production';
      case 'PROCESSING':       return 'Processing';
      case 'SHIPPED':          return 'Shipped';
      case 'OUT_FOR_DELIVERY': return 'Out for delivery';
      case 'DELIVERED':        return 'Delivered';
      case 'CANCELLED':        return 'Cancelled';
      case 'RETURNED':         return 'Returned';
      default:                 return status;
    }
  }

  protected badgeClass(status: string): string {
    switch (status) {
      case 'DELIVERED':
        return 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300';
      case 'CANCELLED':
        return 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300';
      case 'RETURNED':
        return 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300';
      case 'SHIPPED':
      case 'OUT_FOR_DELIVERY':
        return 'bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300';
      case 'ACCEPTED':
      case 'IN_PRODUCTION':
      case 'PROCESSING':
        return 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300';
      default:
        return 'bg-neutral-100 text-neutral-700 dark:bg-neutral-800 dark:text-neutral-300';
    }
  }
}
