import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, of, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiService } from '../core/api.service';
import { ApiCategory, ApiProduct } from '../core/api.models';
import { EcommerceStore } from '../core/store/ecommerce.store';
import { ProductCardComponent } from '../components/shared/product-card.component';
import { RevealDirective } from '../core/reveal.directive';
import { SeoService } from '../core/seo.service';

/**
 * A category's own storefront page, served at the top level from its slug —
 * e.g. /erezer-pink.
 *
 * Registered as the catch-all `:slug` route *after* every real route, so a
 * category can never shadow /shop, /cart and friends. An unknown slug is not an
 * error page: it redirects home, because this route also catches genuine typos
 * that used to hit the wildcard.
 */
@Component({
  selector: 'app-collection-page',
  standalone: true,
  imports: [ProductCardComponent, RouterLink, RevealDirective],
  template: `
    @if (notFound()) {
      <p class="app-muted py-20 text-center text-sm">Collection not found. Taking you home…</p>
    } @else {
      <section class="relative full-bleed mb-8 border-b border-neutral-200 px-4 pb-8 sm:px-6 lg:px-8 dark:border-neutral-800" appReveal>
        <p class="flex items-center gap-3 text-xs font-semibold uppercase tracking-[0.32em] text-neutral-500 dark:text-neutral-400">
          <span class="h-px w-10 bg-neutral-400 dark:bg-neutral-600"></span>
          Collection
        </p>
        <!-- Size set directly: app-section-title hardcodes 1.875rem and ties
             with Tailwind's text-* utilities on specificity. -->
        <h1 class="mt-4 text-4xl font-semibold leading-[1.05] tracking-[-0.02em] md:text-5xl xl:text-6xl">
          {{ category()?.name || 'Collection' }}
        </h1>
        <p class="app-muted mt-4 text-base">
          {{ loading() ? 'Loading…' : (products().length + ' ' + (products().length === 1 ? 'product' : 'products')) }}
        </p>
      </section>

      @if (!loading() && products().length === 0) {
        <div class="py-20 text-center">
          <p class="app-muted text-sm">Nothing in this collection yet.</p>
          <a routerLink="/shop" class="btn-primary mt-6 inline-flex">Browse the shop</a>
        </div>
      } @else {
        <section class="card-grid-flush relative full-bleed grid grid-cols-1 px-4 sm:grid-cols-2 sm:px-6 lg:grid-cols-3 lg:px-8 xl:grid-cols-4 2xl:grid-cols-5">
          @for (product of products(); track product.id; let i = $index) {
            <app-product-card [product]="store.toStoreProduct(product)" [appReveal]="i % 10" />
          }
        </section>
      }
    }
  `,
})
export class CollectionPage {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly seo = inject(SeoService);
  protected readonly store = inject(EcommerceStore);

  protected readonly category = signal<ApiCategory | null>(null);
  protected readonly products = signal<ApiProduct[]>([]);
  protected readonly loading = signal(true);
  protected readonly notFound = signal(false);

  constructor() {
    // paramMap rather than a snapshot: Angular reuses this component when
    // navigating between two collections, and a snapshot would keep showing
    // the first one.
    this.route.paramMap
      .pipe(
        switchMap((params) => {
          const slug = params.get('slug') ?? '';
          this.loading.set(true);
          this.notFound.set(false);
          return this.api.getCategoryBySlug(slug).pipe(catchError(() => of(null)));
        }),
        takeUntilDestroyed(),
      )
      .subscribe((category) => {
        if (!category) {
          this.loading.set(false);
          this.notFound.set(true);
          // This route also catches unmatched URLs, so an unknown slug means a
          // bad link rather than a broken collection.
          void this.router.navigateByUrl('/', { replaceUrl: true });
          return;
        }
        this.category.set(category);
        this.seo.update({ title: category.name, description: `Shop the ${category.name} collection from EREZER.` });
        this.api
          .getProductsByCategory(category.id)
          .pipe(catchError(() => of([] as ApiProduct[])))
          .subscribe((items) => {
            this.products.set(items);
            this.loading.set(false);
          });
      });
  }
}
