import { CurrencyPipe, isPlatformBrowser } from '@angular/common';
import { Component, computed, inject, OnDestroy, OnInit, PLATFORM_ID, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiFlashSale, ApiProduct } from '../../core/api.models';
import { FlashSaleStore } from '../../core/store/flash-sale.store';

/**
 * Compact, attention-grabbing flash-sale promo for the landing page. Renders a
 * dark headline banner + a card with a live countdown and up to four sale
 * items, and a "View all deals →" link to the dedicated Flash Sale page.
 * Renders nothing when no sale is active or the countdown has elapsed.
 */
@Component({
  selector: 'app-flash-sale-widget',
  standalone: true,
  imports: [RouterLink, CurrencyPipe],
  template: `
    @if (sale(); as s) {
      @if (!expired()) {
        <section class="overflow-hidden rounded-3xl bg-neutral-50 p-2 shadow-sm ring-1 ring-black/5 dark:bg-neutral-900 dark:ring-white/10">
          <!-- Headline banner -->
          <div class="rounded-[1.25rem] bg-neutral-900 p-6 text-white sm:p-7">
            <div class="flex flex-wrap items-center justify-between gap-4">
              <div>
                <p class="text-xs font-semibold uppercase tracking-[0.3em] text-white/60">
                  {{ s.label || 'Exclusive offer' }}
                </p>
                <h2 class="mt-2 text-3xl font-semibold uppercase tracking-tight sm:text-4xl">{{ s.name }}</h2>
                <p class="mt-1 text-sm text-white/60">{{ offerSubtitle() }}</p>
              </div>
              @if (s.couponCode) {
                <button
                  type="button"
                  (click)="copyCode(s.couponCode)"
                  class="inline-flex items-center gap-2 rounded-full bg-red-600 px-5 py-2.5 text-sm font-semibold uppercase tracking-wide text-white transition hover:bg-red-500"
                >
                  {{ copied() ? 'Copied!' : 'Copy code' }}
                </button>
              }
            </div>
          </div>

          <!-- Deals card -->
          <div class="px-4 py-5 sm:px-6">
            <div class="mb-5 flex flex-wrap items-center justify-between gap-3">
              <div class="flex items-center gap-2">
                <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 text-amber-500" viewBox="0 0 24 24" fill="currentColor"><path d="M13 2L3 14h7l-1 8 10-12h-7l1-8z"/></svg>
                <span class="text-sm font-bold uppercase tracking-wide">{{ s.name }}</span>
                <span class="rounded-md bg-red-600 px-2 py-0.5 text-xs font-bold text-white">{{ offerBadge() }}</span>
              </div>
              <div class="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-neutral-500 dark:text-neutral-400">
                <span>Ends in</span>
                <span class="rounded-md bg-neutral-900 px-2 py-1 font-mono text-sm text-white tabular-nums dark:bg-neutral-800">{{ pad(countdown().hours) }}</span>:
                <span class="rounded-md bg-neutral-900 px-2 py-1 font-mono text-sm text-white tabular-nums dark:bg-neutral-800">{{ pad(countdown().minutes) }}</span>:
                <span class="rounded-md bg-neutral-900 px-2 py-1 font-mono text-sm text-white tabular-nums dark:bg-neutral-800">{{ pad(countdown().seconds) }}</span>
              </div>
            </div>

            <div class="grid gap-3 sm:grid-cols-2">
              @for (p of previewProducts(); track p.id) {
                <a
                  [routerLink]="['/product', p.id]"
                  class="group flex items-center gap-3 rounded-2xl border border-neutral-200 p-2.5 transition hover:border-neutral-300 hover:shadow-sm dark:border-neutral-800 dark:hover:border-neutral-700"
                >
                  <div class="relative shrink-0">
                    <img [src]="p.imageUrl" [alt]="p.name" class="h-16 w-16 rounded-xl object-cover" />
                    <span class="absolute -left-1 -top-1 rounded-md bg-red-600 px-1.5 py-0.5 text-[10px] font-bold text-white">{{ offerBadge() }}</span>
                  </div>
                  <div class="min-w-0">
                    <p class="truncate text-sm font-semibold">{{ p.name }}</p>
                    <p class="mt-1 flex items-baseline gap-2">
                      <span class="text-sm font-bold text-red-600">{{ salePrice(p) | currency:'BDT':'৳' }}</span>
                      <span class="text-xs text-neutral-400 line-through">{{ p.price | currency:'BDT':'৳' }}</span>
                    </p>
                  </div>
                </a>
              }
            </div>

            <div class="mt-5 border-t border-neutral-200 pt-4 text-center dark:border-neutral-800">
              <a routerLink="/flash-sale" class="inline-flex items-center gap-1.5 text-sm font-semibold text-neutral-700 transition hover:text-black dark:text-neutral-300 dark:hover:text-white">
                View all deals
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5L21 12m0 0l-7.5 7.5M21 12H3"/></svg>
              </a>
            </div>
          </div>
        </section>
      }
    }
  `
})
export class FlashSaleWidgetComponent implements OnInit, OnDestroy {
  private readonly store = inject(FlashSaleStore);
  private readonly platformId = inject(PLATFORM_ID);

  protected readonly sale = this.store.sale;
  protected readonly copied = signal(false);
  protected readonly countdown = signal({ hours: 0, minutes: 0, seconds: 0 });
  protected readonly expired = signal(false);

  private timer: ReturnType<typeof setInterval> | null = null;

  protected readonly previewProducts = computed(() => (this.sale()?.products ?? []).slice(0, 4));

  protected readonly offerBadge = computed(() => this.badge(this.sale()));
  protected readonly offerSubtitle = computed(() => {
    const s = this.sale();
    if (!s) return '';
    if (s.couponCode && s.minSpend) {
      const off = s.discountType === 'PERCENT' ? `${s.discountValue}% off` : `৳${s.discountValue} off`;
      return `${off} your order (min ৳${s.minSpend})`;
    }
    return `${this.badge(s)} on all items`;
  });

  ngOnInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      this.tick();
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

  private badge(s: ApiFlashSale | null): string {
    if (!s) return '';
    return s.discountType === 'PERCENT' ? `${s.discountValue}% OFF` : `৳${s.discountValue} OFF`;
  }

  /** Display sale price: prefer the product's own discountPrice, else derive from the campaign. */
  protected salePrice(p: ApiProduct): number {
    const s = this.sale();
    if (p.discountPrice && p.discountPrice > 0 && p.discountPrice < p.price) return p.discountPrice;
    if (!s) return p.price;
    return s.discountType === 'PERCENT'
      ? Math.round(p.price * (1 - s.discountValue / 100))
      : Math.max(0, p.price - s.discountValue);
  }

  protected copyCode(code: string): void {
    if (!isPlatformBrowser(this.platformId)) return;
    void navigator.clipboard?.writeText(code).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    });
  }
}
