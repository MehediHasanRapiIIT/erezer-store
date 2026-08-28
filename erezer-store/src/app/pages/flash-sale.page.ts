import { CurrencyPipe, DatePipe, isPlatformBrowser } from '@angular/common';
import { Component, computed, DestroyRef, effect, inject, OnDestroy, OnInit, PLATFORM_ID, signal, untracked } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, of, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiFlashSale, ApiProduct } from '../core/api.models';
import { ApiService } from '../core/api.service';
import { EcommerceStore } from '../core/store/ecommerce.store';

/**
 * Flash Sale detail page (/flash-sale/:id). Loads one campaign by id and shows
 * its hero with a live countdown and the full grid of sale items.
 */
@Component({
  standalone: true,
  imports: [RouterLink, CurrencyPipe, DatePipe],
  template: `
    <a routerLink="/flash-sale" class="mb-6 inline-flex items-center gap-1.5 text-sm font-medium text-neutral-500 transition hover:text-black dark:text-neutral-400 dark:hover:text-white">
      <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M10.5 19.5L3 12m0 0l7.5-7.5M3 12h18"/></svg>
      All deals
    </a>

    @if (loading()) {
      <p class="app-muted py-20 text-center">Loading flash sale…</p>
    } @else if (sale(); as s) {
      <!-- ── Hero ─────────────────────────────────────────────────────────── -->
      <section class="mb-12 overflow-hidden rounded-3xl bg-gradient-to-b from-red-50 to-white px-6 py-14 text-center dark:from-neutral-900 dark:to-neutral-950">
        <span class="inline-flex items-center gap-1.5 rounded-full bg-red-600 px-4 py-1.5 text-xs font-bold uppercase tracking-wide text-white">
          <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="currentColor"><path d="M13 2L3 14h7l-1 8 10-12h-7l1-8z"/></svg>
          {{ s.label || 'Limited time' }}
        </span>
        <h1 class="mt-5 text-4xl font-bold tracking-tight sm:text-6xl">{{ s.name }}</h1>
        <p class="mt-3 text-sm text-neutral-500 dark:text-neutral-400">
          {{ s.products.length }} {{ s.products.length === 1 ? 'item' : 'items' }} · Ends {{ s.endsAt | date:'M/d/yyyy, h:mm:ss a' }}
        </p>

        @if (!expired()) {
          <div class="mt-8 flex items-center justify-center gap-3 sm:gap-4">
            @for (unit of countdownUnits(); track unit.label) {
              <div class="flex flex-col items-center">
                <div class="flex h-20 w-20 items-center justify-center rounded-2xl bg-neutral-900 text-3xl font-bold text-white tabular-nums sm:h-24 sm:w-24 sm:text-4xl">
                  {{ pad(unit.value) }}
                </div>
                <span class="mt-2 text-[11px] font-semibold uppercase tracking-wide text-neutral-500 dark:text-neutral-400">{{ unit.label }}</span>
              </div>
            }
          </div>
        } @else {
          <p class="mt-8 text-sm font-semibold uppercase tracking-wide text-red-600">This sale has ended</p>
        }

        <div class="mt-8">
          <span class="inline-flex items-center gap-2 rounded-full bg-red-600 px-6 py-3 text-base font-bold text-white shadow-lg shadow-red-600/30">
            🔥 {{ offerBadge() }} <span class="font-medium">on all items</span>
          </span>
        </div>
      </section>

      <!-- ── Sale items grid ──────────────────────────────────────────────── -->
      <section class="space-y-6">
        <div class="flex items-end justify-between">
          <div class="flex items-center gap-2">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 text-amber-500" viewBox="0 0 24 24" fill="currentColor"><path d="M13 2L3 14h7l-1 8 10-12h-7l1-8z"/></svg>
            <h2 class="app-section-title text-2xl">Sale Items</h2>
            <span class="text-sm text-neutral-500 dark:text-neutral-400">{{ s.products.length }} items</span>
          </div>
          <a routerLink="/shop" class="text-sm font-medium underline underline-offset-4">Browse all</a>
        </div>

        <div class="grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          @for (p of s.products; track p.id) {
            <article class="app-card group flex flex-col overflow-hidden transition-all duration-300 hover:-translate-y-1 hover:shadow-xl hover:shadow-black/10">
              <a [routerLink]="['/product', p.id]" class="relative block overflow-hidden">
                <img [src]="p.imageUrl" [alt]="p.name" class="h-64 w-full object-cover transition-transform duration-700 ease-out group-hover:scale-105" />
                <span class="absolute left-3 top-3 rounded-md bg-red-600 px-2.5 py-1 text-xs font-bold text-white">{{ offerBadge() }}</span>
              </a>
              <div class="flex flex-1 flex-col p-4">
                <a [routerLink]="['/product', p.id]" class="font-semibold tracking-tight underline-offset-4 hover:underline">{{ p.name }}</a>
                @if (p.brand) {
                  <p class="mt-0.5 text-xs uppercase tracking-[0.16em] text-neutral-400">{{ p.brand }}</p>
                }
                <p class="mt-2 flex items-baseline gap-2">
                  <span class="text-lg font-bold text-red-600">{{ salePrice(p) | currency:'BDT':'৳' }}</span>
                  <span class="text-sm text-neutral-400 line-through">{{ p.price | currency:'BDT':'৳' }}</span>
                </p>
                <button
                  type="button"
                  (click)="addToCart(p)"
                  [disabled]="!p.isAvailable"
                  class="btn-primary mt-4 w-full uppercase disabled:opacity-50"
                >
                  {{ p.isAvailable ? 'Add to bag' : 'Sold out' }}
                </button>
              </div>
            </article>
          }
        </div>
      </section>
    } @else {
      <!-- ── No active sale ───────────────────────────────────────────────── -->
      <section class="py-24 text-center">
        <h1 class="text-3xl font-bold tracking-tight">No flash sale right now</h1>
        <p class="mt-3 text-neutral-500 dark:text-neutral-400">Check back soon — or browse the full collection in the meantime.</p>
        <a routerLink="/shop" class="btn-primary mt-6 inline-flex">Shop the collection</a>
      </section>
    }
  `
})
export class FlashSalePage implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ApiService);
  private readonly ecommerce = inject(EcommerceStore);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly sale = signal<ApiFlashSale | null>(null);
  protected readonly loading = signal(true);
  protected readonly countdown = signal({ hours: 0, minutes: 0, seconds: 0 });
  protected readonly expired = signal(false);

  private timer: ReturnType<typeof setInterval> | null = null;

  protected readonly countdownUnits = computed(() => {
    const c = this.countdown();
    return [
      { label: 'Hours', value: c.hours },
      { label: 'Mins', value: c.minutes },
      { label: 'Secs', value: c.seconds },
    ];
  });

  protected readonly offerBadge = computed(() => {
    const s = this.sale();
    if (!s) return '';
    return s.discountType === 'PERCENT' ? `${s.discountValue}% OFF` : `৳${s.discountValue} OFF`;
  });

  constructor() {
    // Seed sale products into the cart store so "Add to bag" can resolve them.
    // seedApiProducts both reads and writes the store's `products` signal, so it
    // must run untracked — otherwise this effect would depend on `products`,
    // re-fire on its own write, and spin forever (freezes the page).
    effect(() => {
      const s = this.sale();
      if (s?.products?.length) untracked(() => this.ecommerce.seedApiProducts(s.products));
    });
  }

  ngOnInit(): void {
    // Reload whenever the :id changes (router reuses the component between sales).
    this.route.paramMap.pipe(
      switchMap((params) => {
        this.loading.set(true);
        this.expired.set(false);
        const id = params.get('id');
        if (!id) return of(null);
        return this.api.getFlashSaleById(id).pipe(catchError(() => of(null)));
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((s) => {
      this.sale.set(s);
      this.loading.set(false);
      this.tick();
    });

    if (isPlatformBrowser(this.platformId)) {
      this.timer = setInterval(() => this.tick(), 1000);
    }
  }

  ngOnDestroy(): void {
    if (this.timer) clearInterval(this.timer);
  }

  private tick(): void {
    const s = this.sale();
    if (!s) return;
    const diff = new Date(s.endsAt).getTime() - Date.now();
    if (diff <= 0) {
      this.expired.set(true);
      this.countdown.set({ hours: 0, minutes: 0, seconds: 0 });
      if (this.timer) clearInterval(this.timer);
      return;
    }
    const totalSeconds = Math.floor(diff / 1000);
    this.countdown.set({
      hours: Math.floor(totalSeconds / 3600),
      minutes: Math.floor((totalSeconds % 3600) / 60),
      seconds: totalSeconds % 60,
    });
  }

  protected pad(n: number): string {
    return String(n).padStart(2, '0');
  }

  protected salePrice(p: ApiProduct): number {
    const s = this.sale();
    if (p.discountPrice && p.discountPrice > 0 && p.discountPrice < p.price) return p.discountPrice;
    if (!s) return p.price;
    return s.discountType === 'PERCENT'
      ? Math.round(p.price * (1 - s.discountValue / 100))
      : Math.max(0, p.price - s.discountValue);
  }

  protected addToCart(p: ApiProduct): void {
    if (!p.isAvailable) return;
    this.ecommerce.addToCart(String(p.id), 'One Size', 1, { unitPrice: this.salePrice(p) });
  }
}
