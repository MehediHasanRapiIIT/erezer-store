import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ApiService } from '../../core/api.service';
import type { ApiBundleOffer } from '../../core/api.models';

/**
 * Landing-page promo for the featured bundle offer ("Buy X Get Y"). Renders a
 * dark headline banner + a preview of the curated products and a CTA to build
 * the bundle. Renders nothing when no featured bundle is active.
 */
@Component({
  selector: 'app-bundle-widget',
  standalone: true,
  imports: [RouterLink, CurrencyPipe],
  template: `
    @if (bundle(); as b) {
      <section class="overflow-hidden rounded-3xl bg-neutral-50 p-2 shadow-sm ring-1 ring-black/5 dark:bg-neutral-900 dark:ring-white/10">
        <!-- Headline banner -->
        <div class="rounded-[1.25rem] bg-neutral-900 p-6 text-white sm:p-7">
          <div class="flex flex-wrap items-center justify-between gap-4">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.3em] text-white/60">{{ b.label || 'Bundle deal' }}</p>
              <h2 class="mt-2 text-3xl font-semibold uppercase tracking-tight sm:text-4xl">{{ b.name }}</h2>
              <p class="mt-1 text-sm text-white/60">Buy {{ b.buyCount }} Get {{ b.getCount }} — pick {{ b.slots }} items.</p>
            </div>
            <div class="text-right">
              @if (b.compareAtPrice) { <p class="text-sm text-white/40 line-through">{{ b.compareAtPrice | currency:'BDT':'৳' }}</p> }
              <p class="text-2xl font-bold">{{ b.bundlePrice | currency:'BDT':'৳' }}</p>
              @if (b.savings && b.savings > 0) {
                <span class="mt-1 inline-block rounded-md bg-emerald-600 px-2 py-0.5 text-xs font-bold">Save {{ b.savings | currency:'BDT':'৳' }}</span>
              }
            </div>
          </div>
        </div>

        <!-- Preview + CTA -->
        <div class="px-4 py-5 sm:px-6">
          <div class="mb-5 flex flex-wrap items-center justify-between gap-3">
            <div class="flex items-center gap-2">
              <span class="text-lg">🎁</span>
              <span class="text-sm font-bold uppercase tracking-wide">Mix &amp; match</span>
              @if (b.compareAtPrice && discountPct() > 0) {
                <span class="rounded-md bg-red-600 px-2 py-0.5 text-xs font-bold text-white">{{ discountPct() }}% OFF</span>
              }
            </div>
            <a [routerLink]="['/bundles', b.id]" class="inline-flex items-center gap-1.5 rounded-full bg-neutral-900 px-4 py-2 text-sm font-semibold text-white transition hover:bg-neutral-700 dark:bg-white dark:text-neutral-900 dark:hover:bg-neutral-200">
              Build your bundle
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5L21 12m0 0l-7.5 7.5M21 12H3"/></svg>
            </a>
          </div>

          <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
            @for (p of previewProducts(); track p.id) {
              <a [routerLink]="['/bundles', b.id]" class="group overflow-hidden rounded-2xl border border-neutral-200 transition hover:border-neutral-300 dark:border-neutral-800 dark:hover:border-neutral-700">
                @if (p.imageUrl) {
                  <img [src]="p.imageUrl" [alt]="p.name" class="aspect-square w-full object-cover transition group-hover:scale-105" />
                } @else {
                  <div class="flex aspect-square items-center justify-center bg-neutral-100 text-3xl dark:bg-neutral-800">👕</div>
                }
              </a>
            }
          </div>

          <div class="mt-5 border-t border-neutral-200 pt-4 text-center dark:border-neutral-800">
            <a routerLink="/bundles" class="inline-flex items-center gap-1.5 text-sm font-semibold text-neutral-700 transition hover:text-black dark:text-neutral-300 dark:hover:text-white">
              View all bundles
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5L21 12m0 0l-7.5 7.5M21 12H3"/></svg>
            </a>
          </div>
        </div>
      </section>
    }
  `,
})
export class BundleWidgetComponent implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly bundle = signal<ApiBundleOffer | null>(null);
  protected readonly previewProducts = computed(() => (this.bundle()?.products ?? []).slice(0, 4));
  protected readonly discountPct = computed(() => {
    const b = this.bundle();
    if (!b?.compareAtPrice || b.compareAtPrice <= 0) return 0;
    return Math.round(((b.compareAtPrice - b.bundlePrice) / b.compareAtPrice) * 100);
  });

  ngOnInit(): void {
    this.api.getFeaturedBundle().pipe(catchError(() => of(null))).subscribe((b) => this.bundle.set(b));
  }
}
