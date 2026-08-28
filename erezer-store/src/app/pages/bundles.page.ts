import { Component, inject, OnInit, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ApiService } from '../core/api.service';
import type { ApiBundleOffer } from '../core/api.models';

@Component({
  standalone: true,
  imports: [CurrencyPipe, RouterLink],
  template: `
    <section class="mx-auto max-w-6xl px-4 py-10">
      <header class="mb-6">
        <h1 class="app-section-title">Bundle deals</h1>
        <p class="app-muted text-sm">Buy more, save more — mix and match from curated picks.</p>
      </header>

      @if (loading()) {
        <p class="app-muted py-16 text-center text-sm">Loading bundles…</p>
      } @else if (bundles().length === 0) {
        <p class="app-muted py-16 text-center text-sm">No bundle deals right now — check back soon.</p>
      } @else {
        <div class="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          @for (b of bundles(); track b.id) {
            <a [routerLink]="['/bundles', b.id]" class="app-card group overflow-hidden">
              <div class="relative aspect-square overflow-hidden bg-neutral-100 dark:bg-neutral-900">
                @if (b.images[0]) {
                  <img [src]="b.images[0]" [alt]="b.name" class="h-full w-full object-cover transition group-hover:scale-105" />
                } @else {
                  <div class="flex h-full items-center justify-center text-5xl">🎁</div>
                }
                @if (b.savings && b.savings > 0) {
                  <span class="absolute left-3 top-3 rounded bg-red-500 px-2 py-1 text-xs font-bold text-white">Save {{ b.savings | currency:'BDT':'৳' }}</span>
                }
              </div>
              <div class="space-y-1 p-4">
                <p class="text-xs font-semibold uppercase tracking-wide text-amber-600">Buy {{ b.buyCount }} Get {{ b.getCount }}</p>
                <h3 class="line-clamp-2 font-medium">{{ b.name }}</h3>
                <div class="flex items-center gap-2">
                  @if (b.compareAtPrice) { <span class="text-sm text-neutral-400 line-through">{{ b.compareAtPrice | currency:'BDT':'৳' }}</span> }
                  <span class="font-bold">{{ b.bundlePrice | currency:'BDT':'৳' }}</span>
                </div>
              </div>
            </a>
          }
        </div>
      }
    </section>
  `,
})
export class BundlesPage implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly bundles = signal<ApiBundleOffer[]>([]);
  protected readonly loading = signal(true);

  ngOnInit(): void {
    this.api.getBundles().pipe(catchError(() => of([] as ApiBundleOffer[]))).subscribe((list) => {
      this.bundles.set(list);
      this.loading.set(false);
    });
  }
}
