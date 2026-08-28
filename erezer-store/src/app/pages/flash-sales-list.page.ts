import { DatePipe, DecimalPipe, NgClass, isPlatformBrowser } from '@angular/common';
import { Component, inject, OnDestroy, OnInit, PLATFORM_ID, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiFlashSale } from '../core/api.models';
import { FlashSaleStore } from '../core/store/flash-sale.store';

/**
 * "View all deals" — lists every currently-active flash sale as a card.
 * Clicking a card opens that campaign's detail page (/flash-sale/:id).
 */
@Component({
  standalone: true,
  imports: [RouterLink, DatePipe, DecimalPipe, NgClass],
  template: `
    <section class="mb-10 text-center">
      <span class="inline-flex items-center gap-1.5 rounded-full bg-red-600 px-4 py-1.5 text-xs font-bold uppercase tracking-wide text-white">
        <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="currentColor"><path d="M13 2L3 14h7l-1 8 10-12h-7l1-8z"/></svg>
        Flash Sales
      </span>
      <h1 class="mt-4 text-4xl font-bold tracking-tight sm:text-5xl">All deals</h1>
      <p class="mt-3 text-sm text-neutral-500 dark:text-neutral-400">Limited-time offers — grab them before the timer runs out.</p>
    </section>

    @if (listLoading()) {
      <p class="app-muted py-16 text-center">Loading flash sales…</p>
    } @else if (sales().length === 0) {
      <section class="py-20 text-center">
        <h2 class="text-2xl font-bold tracking-tight">No flash sales right now</h2>
        <p class="mt-3 text-neutral-500 dark:text-neutral-400">Check back soon — or browse the full collection.</p>
        <a routerLink="/shop" class="btn-primary mt-6 inline-flex">Shop the collection</a>
      </section>
    } @else {
      <div class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        @for (s of sales(); track s.id) {
          <a [routerLink]="upcoming(s) ? null : ['/flash-sale', s.id]"
            class="app-card flex flex-col overflow-hidden p-0 transition-all duration-300"
            [ngClass]="upcoming(s)
              ? 'cursor-not-allowed opacity-80'
              : 'group cursor-pointer hover:-translate-y-1 hover:shadow-xl hover:shadow-black/10'">
            <!-- Cover: collage of up to 3 product images -->
            <div class="relative grid h-40 grid-cols-3 gap-0.5 overflow-hidden bg-neutral-100 dark:bg-neutral-800">
              @for (p of cover(s); track p.id) {
                <img [src]="p.imageUrl" [alt]="p.name" class="h-full w-full object-cover transition-transform duration-700 group-hover:scale-105" />
              } @empty {
                <div class="col-span-3 flex items-center justify-center text-neutral-400">
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-10 w-10" viewBox="0 0 24 24" fill="currentColor"><path d="M13 2L3 14h7l-1 8 10-12h-7l1-8z"/></svg>
                </div>
              }
              <span class="absolute left-3 top-3 rounded-md bg-red-600 px-2.5 py-1 text-xs font-bold text-white">{{ badge(s) }}</span>
              @if (upcoming(s)) {
                <span class="absolute right-3 top-3 rounded-md bg-neutral-900/90 px-2 py-1 text-[10px] font-bold uppercase text-white">Upcoming</span>
              } @else if (s.featured) {
                <span class="absolute right-3 top-3 rounded-md bg-amber-500 px-2 py-1 text-[10px] font-bold uppercase text-white">Featured</span>
              }
            </div>

            <div class="flex flex-1 flex-col p-5">
              @if (s.label) {
                <p class="text-[11px] font-semibold uppercase tracking-[0.22em] text-neutral-400">{{ s.label }}</p>
              }
              <h2 class="mt-1 text-xl font-bold tracking-tight">{{ s.name }}</h2>
              <p class="mt-1 text-sm text-neutral-500 dark:text-neutral-400">
                {{ s.products.length }} {{ s.products.length === 1 ? 'item' : 'items' }} ·
                @if (upcoming(s)) { Starts {{ s.startsAt | date:'MMM d, h:mm a' }} }
                @else { Ends {{ s.endsAt | date:'MMM d, h:mm a' }} }
              </p>

              <div class="mt-4 flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-neutral-500 dark:text-neutral-400">
                <span>{{ upcoming(s) ? 'Starts in' : 'Ends in' }}</span>
                <span class="rounded-md bg-neutral-900 px-2 py-1 font-mono text-sm text-white tabular-nums dark:bg-neutral-800">{{ remaining(s) }}</span>
              </div>

              @if (s.minSpend) {
                <p class="mt-3 text-xs text-neutral-500 dark:text-neutral-400">Min spend ৳{{ s.minSpend | number }}</p>
              }

              @if (upcoming(s)) {
                <span class="mt-5 inline-flex items-center gap-1.5 text-sm font-semibold text-neutral-400">
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z"/></svg>
                  Available when it starts
                </span>
              } @else {
                <span class="mt-5 inline-flex items-center gap-1.5 text-sm font-semibold text-neutral-700 group-hover:text-black dark:text-neutral-300 dark:group-hover:text-white">
                  View deals
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 transition-transform group-hover:translate-x-1" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5L21 12m0 0l-7.5 7.5M21 12H3"/></svg>
                </span>
              }
            </div>
          </a>
        }
      </div>
    }
  `
})
export class FlashSalesListPage implements OnInit, OnDestroy {
  private readonly store = inject(FlashSaleStore);
  private readonly platformId = inject(PLATFORM_ID);

  protected readonly sales = this.store.sales;
  protected readonly listLoading = this.store.listLoading;

  /** Re-render the per-card countdown each second. */
  protected readonly nowTick = signal(0);
  private timer: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.store.reloadList();
    if (isPlatformBrowser(this.platformId)) {
      this.timer = setInterval(() => this.nowTick.update((n) => n + 1), 1000);
    }
  }

  ngOnDestroy(): void {
    if (this.timer) clearInterval(this.timer);
  }

  protected cover(s: ApiFlashSale): ApiFlashSale['products'] {
    return s.products.slice(0, 3);
  }

  protected badge(s: ApiFlashSale): string {
    return s.discountType === 'PERCENT' ? `${s.discountValue}% OFF` : `৳${s.discountValue} OFF`;
  }

  /** True when the sale hasn't started yet (scheduled for the future). */
  protected upcoming(s: ApiFlashSale): boolean {
    this.nowTick();
    return !!s.startsAt && new Date(s.startsAt).getTime() > Date.now();
  }

  /** Compact countdown — to start when upcoming, otherwise to end. Reads nowTick so it refreshes. */
  protected remaining(s: ApiFlashSale): string {
    this.nowTick();
    const target = this.upcoming(s) ? new Date(s.startsAt!).getTime() : new Date(s.endsAt).getTime();
    const diff = target - Date.now();
    if (diff <= 0) return 'Ended';
    const total = Math.floor(diff / 1000);
    const days = Math.floor(total / 86400);
    const h = Math.floor((total % 86400) / 3600);
    const m = Math.floor((total % 3600) / 60);
    const sec = total % 60;
    const pad = (n: number) => String(n).padStart(2, '0');
    return (days > 0 ? `${days}d ` : '') + `${pad(h)}:${pad(m)}:${pad(sec)}`;
  }
}
