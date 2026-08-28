import { Component, computed, effect, inject, OnInit, PLATFORM_ID, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, of, Subject, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiService } from '../core/api.service';
import { ApiCategory, ApiProduct } from '../core/api.models';
import { EcommerceStore } from '../core/store/ecommerce.store';
import { ProductCardComponent } from '../components/shared/product-card.component';
import { RevealDirective } from '../core/reveal.directive';

@Component({
  standalone: true,
  imports: [FormsModule, ProductCardComponent, RouterLink, RevealDirective],
  template: `
    <!-- ── Editorial header ────────────────────────────────────────────────── -->
    <section class="mb-8 flex flex-col gap-5 md:flex-row md:items-end md:justify-between" appReveal>
      <div class="space-y-2">
        <p class="text-xs font-semibold uppercase tracking-[0.28em] text-neutral-500 dark:text-neutral-400">The collection</p>
        <h1 class="app-section-title text-3xl md:text-4xl">Shop</h1>
        <p class="app-muted max-w-md">Discover premium essentials for everyday style.</p>
      </div>
      <label class="relative w-full md:w-96">
        <svg xmlns="http://www.w3.org/2000/svg" class="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400"
          fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-5.2-5.2M17 10a7 7 0 11-14 0 7 7 0 0114 0z" />
        </svg>
        <input
          [ngModel]="searchQuery()"
          (ngModelChange)="onSearchChange($event)"
          placeholder="Search products, collections..."
          class="w-full rounded-full border border-neutral-300 bg-white py-3 pl-11 pr-4 text-sm outline-none transition focus:border-neutral-500 dark:border-neutral-700 dark:bg-neutral-900 dark:focus:border-neutral-500"
        />
      </label>
    </section>

    <!-- ── Category strip (image tiles) ────────────────────────────────────── -->
    <section class="mb-8" appReveal>
      <div class="no-scrollbar -mx-1 flex gap-3 overflow-x-auto px-1 pb-2">
        <!-- All -->
        <button (click)="selectCategory(null)"
          class="group relative h-28 w-24 flex-shrink-0 overflow-hidden rounded-2xl border transition"
          [class.border-transparent]="selectedCategoryId() === null"
          [class.ring-2]="selectedCategoryId() === null"
          [class.ring-neutral-900]="selectedCategoryId() === null"
          [class.dark:ring-white]="selectedCategoryId() === null"
          [class.border-neutral-200]="selectedCategoryId() !== null"
          [class.dark:border-neutral-800]="selectedCategoryId() !== null">
          <div class="absolute inset-0 bg-gradient-to-br from-neutral-800 to-neutral-950"></div>
          <span class="absolute inset-x-0 bottom-2 px-2 text-center text-xs font-semibold text-white">All</span>
        </button>

        @for (cat of categories(); track cat.id) {
          <button (click)="selectCategory(cat.id)"
            class="group relative h-28 w-24 flex-shrink-0 overflow-hidden rounded-2xl border transition"
            [class.border-transparent]="selectedCategoryId() === cat.id"
            [class.ring-2]="selectedCategoryId() === cat.id"
            [class.ring-neutral-900]="selectedCategoryId() === cat.id"
            [class.dark:ring-white]="selectedCategoryId() === cat.id"
            [class.border-neutral-200]="selectedCategoryId() !== cat.id"
            [class.dark:border-neutral-800]="selectedCategoryId() !== cat.id">
            @if (cat.imageUrl) {
              <img [src]="cat.imageUrl" [alt]="cat.name"
                class="h-full w-full object-cover transition-transform duration-500 group-hover:scale-110" />
            } @else {
              <div class="h-full w-full bg-gradient-to-br from-neutral-200 to-neutral-300 dark:from-neutral-700 dark:to-neutral-800"></div>
            }
            <div class="absolute inset-0 bg-gradient-to-t from-black/70 via-black/10 to-transparent"></div>
            <span class="absolute inset-x-0 bottom-2 px-2 text-center text-xs font-semibold text-white">{{ cat.name }}</span>
          </button>
        }
      </div>
    </section>

    <!-- ── Sticky filter toolbar ───────────────────────────────────────────── -->
    <!-- top offset clears the global sticky header (~60px) so it pins just below it -->
    <section class="sticky top-16 z-20 mb-5">
      <div class="toolbar-in flex flex-wrap items-center gap-2.5 rounded-2xl border border-neutral-200/70 bg-white/70 px-3 py-2.5 shadow-lg shadow-black/5 backdrop-blur-xl transition-shadow hover:shadow-xl hover:shadow-black/[0.07] dark:border-neutral-800/70 dark:bg-neutral-900/70">
        <!-- Result count -->
        <div class="mr-auto flex items-baseline gap-1.5 pl-1.5">
          <span class="text-lg font-semibold tabular-nums leading-none text-neutral-900 dark:text-neutral-100">{{ displayCount() }}</span>
          <span class="text-[11px] font-medium uppercase tracking-[0.18em] app-muted">{{ resultCount() === 1 ? 'result' : 'results' }}</span>
        </div>

        <!-- Desktop facets -->
        <div class="hidden items-center gap-2 md:flex">
          @if (availableGenders().length > 0) {
            <div class="relative">
              <select [ngModel]="selectedGender()" (ngModelChange)="selectedGender.set($event)" class="ctrl peer capitalize">
                <option [ngValue]="null">All genders</option>
                @for (g of availableGenders(); track g) { <option [ngValue]="g">{{ g }}</option> }
              </select>
              <svg class="chevron" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7"/></svg>
            </div>
          }
          @if (availableBrands().length > 0) {
            <div class="relative">
              <select [ngModel]="selectedBrand()" (ngModelChange)="selectedBrand.set($event)" class="ctrl peer">
                <option [ngValue]="null">All brands</option>
                @for (b of availableBrands(); track b) { <option [ngValue]="b">{{ b }}</option> }
              </select>
              <svg class="chevron" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7"/></svg>
            </div>
          }
          <div class="relative">
            <span class="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-sm text-neutral-400">৳</span>
            <input type="number" min="0" [ngModel]="maxPrice()" (ngModelChange)="setMaxPrice($event)"
              [placeholder]="'Max ' + (priceCeiling() || 'price')"
              class="ctrl w-32 !pl-7" />
          </div>
        </div>

        <!-- Sort -->
        <div class="relative">
          <select [ngModel]="sortBy()" (ngModelChange)="sortBy.set($event)" class="ctrl peer pl-9">
            @for (opt of sortOptions; track opt.value) {
              <option [ngValue]="opt.value">{{ opt.label }}</option>
            }
          </select>
          <svg class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M3 7h13M3 12h9M3 17h5m9-9v10m0 0l3-3m-3 3l-3-3"/></svg>
          <svg class="chevron" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7"/></svg>
        </div>

        <!-- Mobile filter trigger -->
        <button type="button" (click)="filtersOpen.set(true)"
          class="inline-flex items-center gap-1.5 rounded-full border border-neutral-200 bg-white/60 px-3.5 py-2 text-sm font-medium transition hover:border-neutral-400 active:scale-95 md:hidden dark:border-neutral-700 dark:bg-neutral-800/60">
          <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M3 4h18M6 12h12M10 20h4" />
          </svg>
          Filters
          @if (activeFacetCount() > 0) {
            <span class="badge-pop ml-0.5 inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-neutral-900 px-1 text-[11px] font-semibold text-white dark:bg-white dark:text-black">{{ activeFacetCount() }}</span>
          }
        </button>

        <!-- Wishlist -->
        <a routerLink="/wishlist"
          class="hidden items-center gap-1.5 rounded-full border border-neutral-200 bg-white/60 px-3.5 py-2 text-sm font-medium transition hover:border-neutral-400 active:scale-95 sm:inline-flex dark:border-neutral-700 dark:bg-neutral-800/60">
          <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 text-rose-500" viewBox="0 0 24 24" fill="currentColor"><path d="M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 7.22 9 12 9 12s9-4.78 9-12z"/></svg>
          <span class="tabular-nums">{{ store.wishlistProducts().length }}</span>
        </a>
      </div>
    </section>

    <!-- ── Active filter chips ─────────────────────────────────────────────── -->
    @if (hasActiveFilters()) {
      <div class="mb-6 flex flex-wrap items-center gap-2">
        <span class="text-[11px] font-semibold uppercase tracking-[0.18em] app-muted">Filtered by</span>
        @if (selectedGender(); as g) {
          <button (click)="toggleGender(g)" class="chip">{{ g }} <span class="chip-x">×</span></button>
        }
        @if (selectedBrand(); as b) {
          <button (click)="selectedBrand.set(null)" class="chip">{{ b }} <span class="chip-x">×</span></button>
        }
        @if (maxPrice() !== null) {
          <button (click)="setMaxPrice(null)" class="chip">≤ ৳{{ maxPrice() }} <span class="chip-x">×</span></button>
        }
        <button (click)="clearFacets()" class="text-xs font-medium underline underline-offset-2 text-neutral-500 transition hover:text-neutral-900 dark:hover:text-neutral-100">Clear all</button>
      </div>
    }

    <!-- ── Product grid ────────────────────────────────────────────────────── -->
    @if (loading()) {
      <section class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        @for (s of skeletons; track s) {
          <div class="app-card overflow-hidden">
            <div class="h-80 w-full animate-pulse bg-neutral-200 dark:bg-neutral-800"></div>
            <div class="space-y-3 p-5">
              <div class="h-3 w-1/3 animate-pulse rounded bg-neutral-200 dark:bg-neutral-800"></div>
              <div class="h-4 w-2/3 animate-pulse rounded bg-neutral-200 dark:bg-neutral-800"></div>
              <div class="h-3 w-1/2 animate-pulse rounded bg-neutral-200 dark:bg-neutral-800"></div>
            </div>
          </div>
        }
      </section>
    } @else {
      <section class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        @for (product of pagedProducts(); track product.id; let i = $index) {
          <app-product-card [product]="store.toStoreProduct(product)" [appReveal]="i % pageSize" />
        } @empty {
          <div class="col-span-full flex flex-col items-center justify-center gap-3 py-20 text-center">
            <div class="inline-flex h-14 w-14 items-center justify-center rounded-full bg-neutral-100 text-neutral-400 dark:bg-neutral-800">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-7 w-7" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
                <path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-5.2-5.2M17 10a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </div>
            <p class="font-semibold">No products match your filters.</p>
            <p class="app-muted text-sm">Try removing a filter or searching for something else.</p>
            @if (hasActiveFilters()) {
              <button (click)="clearFacets()" class="btn-secondary mt-1">Clear filters</button>
            }
          </div>
        }
      </section>

      <!-- ── Load more ─────────────────────────────────────────────────────── -->
      @if (resultCount() > 0) {
        <div class="mt-10 flex flex-col items-center gap-3">
          <p class="app-muted text-sm tabular-nums">Showing {{ pagedProducts().length }} of {{ resultCount() }}</p>
          @if (canLoadMore()) {
            <button type="button" (click)="loadMore()" class="btn-secondary px-8">Load more</button>
          }
        </div>
      }
    }

    <!-- ── Mobile filter drawer ────────────────────────────────────────────── -->
    @if (filtersOpen()) {
      <div class="fixed inset-0 z-40 md:hidden">
        <div class="absolute inset-0 bg-black/40 backdrop-blur-sm" (click)="filtersOpen.set(false)"></div>
        <div class="absolute right-0 top-0 flex h-full w-80 max-w-[85vw] flex-col bg-white p-6 shadow-2xl dark:bg-neutral-950">
          <div class="mb-6 flex items-center justify-between">
            <h2 class="text-lg font-semibold">Filters</h2>
            <button (click)="filtersOpen.set(false)" aria-label="Close" class="text-neutral-400 hover:text-neutral-900 dark:hover:text-neutral-100">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          <div class="flex-1 space-y-6 overflow-y-auto">
            @if (availableGenders().length > 0) {
              <div class="space-y-2">
                <p class="text-xs font-semibold uppercase tracking-wider text-neutral-500">Gender</p>
                <div class="flex flex-wrap gap-2">
                  @for (g of availableGenders(); track g) {
                    <button (click)="toggleGender(g)"
                      class="rounded-full border px-3 py-1.5 text-sm capitalize transition"
                      [class.border-neutral-900]="selectedGender() === g" [class.bg-neutral-900]="selectedGender() === g"
                      [class.text-white]="selectedGender() === g"
                      [class.border-neutral-300]="selectedGender() !== g" [class.dark:border-neutral-700]="selectedGender() !== g">{{ g }}</button>
                  }
                </div>
              </div>
            }
            @if (availableBrands().length > 0) {
              <div class="space-y-2">
                <p class="text-xs font-semibold uppercase tracking-wider text-neutral-500">Brand</p>
                <select [ngModel]="selectedBrand()" (ngModelChange)="selectedBrand.set($event)"
                  class="w-full rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900">
                  <option [ngValue]="null">All brands</option>
                  @for (b of availableBrands(); track b) { <option [ngValue]="b">{{ b }}</option> }
                </select>
              </div>
            }
            <div class="space-y-2">
              <p class="text-xs font-semibold uppercase tracking-wider text-neutral-500">Max price</p>
              <input type="number" min="0" [ngModel]="maxPrice()" (ngModelChange)="setMaxPrice($event)"
                [placeholder]="'Max ৳' + (priceCeiling() || '')"
                class="w-full rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900" />
            </div>
          </div>

          <div class="mt-6 flex gap-3">
            <button (click)="clearFacets()" class="btn-secondary flex-1">Clear</button>
            <button (click)="filtersOpen.set(false)" class="btn-primary flex-1">Show {{ resultCount() }}</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .no-scrollbar::-webkit-scrollbar { display: none; }
    .no-scrollbar { -ms-overflow-style: none; scrollbar-width: none; }

    /* Unified pill control (select / input) */
    .ctrl {
      appearance: none; -webkit-appearance: none;
      border-radius: 9999px;
      border: 1px solid rgb(229 229 229);
      background: rgba(255, 255, 255, 0.55);
      padding: 0.5rem 2.1rem 0.5rem 0.95rem;
      font-size: 0.875rem; font-weight: 500;
      color: rgb(38 38 38); cursor: pointer;
      transition: border-color .2s ease, box-shadow .2s ease, background-color .2s ease;
    }
    .ctrl:hover { border-color: rgb(163 163 163); }
    .ctrl:focus {
      outline: none; border-color: rgb(23 23 23);
      box-shadow: 0 0 0 3px rgba(0, 0, 0, 0.07);
      background: white;
    }
    :host-context(.dark) .ctrl {
      border-color: rgb(64 64 64); background: rgba(38, 38, 38, 0.55); color: rgb(229 229 229);
    }
    :host-context(.dark) .ctrl:hover { border-color: rgb(115 115 115); }
    :host-context(.dark) .ctrl:focus {
      border-color: white; box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.14); background: rgb(23 23 23);
    }
    .chevron {
      position: absolute; right: 0.7rem; top: 50%;
      height: 0.95rem; width: 0.95rem; margin-top: -0.475rem;
      color: rgb(115 115 115); pointer-events: none;
      transition: transform .25s ease;
    }
    .peer:focus ~ .chevron { transform: rotate(180deg); }

    /* Toolbar entrance */
    @keyframes toolbarIn { from { opacity: 0; transform: translateY(-10px); } to { opacity: 1; transform: none; } }
    .toolbar-in { animation: toolbarIn .5s cubic-bezier(.22, 1, .36, 1) both; }

    /* Mobile active-count badge pop */
    @keyframes badgePop { 0% { transform: scale(0); } 60% { transform: scale(1.25); } 100% { transform: scale(1); } }
    .badge-pop { animation: badgePop .3s cubic-bezier(.22, 1, .36, 1) both; }

    /* Filter chips */
    @keyframes chipIn { from { opacity: 0; transform: scale(.85); } to { opacity: 1; transform: none; } }
    .chip {
      display: inline-flex; align-items: center; gap: 0.4rem;
      border-radius: 9999px; border: 1px solid transparent;
      background: rgb(245 245 245);
      padding: 0.32rem 0.4rem 0.32rem 0.8rem; font-size: 0.8125rem; font-weight: 500;
      text-transform: capitalize;
      transition: background-color .15s ease, transform .15s ease;
      animation: chipIn .25s cubic-bezier(.22, 1, .36, 1) both;
    }
    .chip:hover { background: rgb(235 235 235); transform: translateY(-1px); }
    :host-context(.dark) .chip { background: rgb(38 38 38); }
    :host-context(.dark) .chip:hover { background: rgb(50 50 50); }
    .chip-x {
      display: inline-flex; align-items: center; justify-content: center;
      height: 1.05rem; width: 1.05rem; border-radius: 9999px;
      font-size: 0.95rem; line-height: 1; color: rgb(115 115 115);
      transition: background-color .15s ease, color .15s ease, transform .2s ease;
    }
    .chip:hover .chip-x { background: rgba(0,0,0,.08); color: rgb(23 23 23); transform: rotate(90deg); }
    :host-context(.dark) .chip:hover .chip-x { background: rgba(255,255,255,.15); color: white; }
  `]
})
export class ShopPage implements OnInit {
  protected readonly store = inject(EcommerceStore);
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly platformId = inject(PLATFORM_ID);

  // ── state signals ──────────────────────────────────────────────────────────
  protected readonly loading           = signal(false);
  protected readonly categories        = signal<ApiCategory[]>([]);
  protected readonly allProducts       = signal<ApiProduct[]>([]);
  protected readonly searchQuery       = signal('');
  protected readonly selectedCategoryId = signal<number | null>(null);
  protected readonly sortBy            = signal<'featured' | 'price-asc' | 'price-desc'>('featured');

  // ── facets ─────────────────────────────────────────────────────────────────
  protected readonly selectedGender = signal<string | null>(null);
  protected readonly selectedBrand  = signal<string | null>(null);
  protected readonly maxPrice       = signal<number | null>(null);

  // ── UI state ─────────────────────────────────────────────────────────────────
  protected readonly filtersOpen = signal(false);
  protected readonly skeletons = [0, 1, 2, 3, 4, 5];

  // ── pagination + animated count ─────────────────────────────────────────────
  protected readonly pageSize = 9;
  protected readonly visibleCount = signal(this.pageSize);
  /** rAF-tweened mirror of resultCount, animated on load and filter changes. */
  protected readonly displayCount = signal(0);
  private countRaf?: number;

  // RxJS search subject → signal bridge
  private readonly search$ = new Subject<string>();

  protected readonly sortOptions = [
    { label: 'Sort: Featured',      value: 'featured'   },
    { label: 'Price: Low to High',  value: 'price-asc'  },
    { label: 'Price: High to Low',  value: 'price-desc' },
  ] as const;

  // ── derived: facet options ──────────────────────────────────────────────────
  protected readonly availableGenders = computed(() =>
    [...new Set(this.allProducts().map((p) => p.gender).filter((g): g is string => !!g))].sort());

  protected readonly availableBrands = computed(() =>
    [...new Set(this.allProducts().map((p) => p.brand).filter((b): b is string => !!b))].sort());

  protected readonly priceCeiling = computed(() => {
    const prices = this.allProducts().map((p) => p.discountPrice ?? p.price);
    return prices.length ? String(Math.ceil(Math.max(...prices))) : '';
  });

  // ── derived: faceted + sorted list ──────────────────────────────────────────
  protected readonly sortedProducts = computed(() => {
    const gender = this.selectedGender();
    const brand = this.selectedBrand();
    const max = this.maxPrice();
    let products = this.allProducts().filter((p) => {
      if (gender && p.gender !== gender) return false;
      if (brand && p.brand !== brand) return false;
      if (max != null && (p.discountPrice ?? p.price) > max) return false;
      return true;
    });
    const sort = this.sortBy();
    const priceOf = (p: ApiProduct) => p.discountPrice ?? p.price;
    if (sort === 'price-asc')  return [...products].sort((a, b) => priceOf(a) - priceOf(b));
    if (sort === 'price-desc') return [...products].sort((a, b) => priceOf(b) - priceOf(a));
    return products; // server order = featured
  });

  protected readonly resultCount = computed(() => this.sortedProducts().length);

  protected readonly activeFacetCount = computed(() =>
    (this.selectedGender() ? 1 : 0) + (this.selectedBrand() ? 1 : 0) + (this.maxPrice() !== null ? 1 : 0));

  protected readonly hasActiveFilters = computed(() => this.activeFacetCount() > 0);

  /** Current page slice and whether more remain. */
  protected readonly pagedProducts = computed(() => this.sortedProducts().slice(0, this.visibleCount()));
  protected readonly canLoadMore = computed(() => this.visibleCount() < this.resultCount());

  protected loadMore(): void {
    this.visibleCount.update((v) => v + this.pageSize);
  }

  protected toggleGender(g: string): void {
    this.selectedGender.update((cur) => (cur === g ? null : g));
  }

  protected setMaxPrice(value: number | string | null): void {
    const n = value === '' || value == null ? null : Number(value);
    this.maxPrice.set(n != null && !isNaN(n) ? n : null);
  }

  protected clearFacets(): void {
    this.selectedGender.set(null);
    this.selectedBrand.set(null);
    this.maxPrice.set(null);
  }

  constructor() {
    // wire up debounced search using takeUntilDestroyed (no manual unsubscribe needed)
    this.search$.pipe(
      debounceTime(350),
      distinctUntilChanged(),
      switchMap((q) => {
        this.loading.set(true);
        if (q.trim().length === 0) {
          return this.fetchProducts();
        }
        return this.api.searchProducts(q).pipe(catchError(() => of([])));
      }),
      takeUntilDestroyed()
    ).subscribe((products) => {
      this.allProducts.set(products);
      this.loading.set(false);
    });

    // Reset to the first page whenever the filtered/sorted set changes.
    effect(() => {
      this.sortedProducts();
      this.visibleCount.set(this.pageSize);
    }, { allowSignalWrites: true });

    // Animate the toolbar count toward the live result count.
    effect(() => {
      this.animateCount(this.resultCount());
    }, { allowSignalWrites: true });
  }

  /** Tween displayCount → target (easeOutCubic). Jumps instantly on SSR / reduced motion. */
  private animateCount(target: number): void {
    const reduced =
      !isPlatformBrowser(this.platformId) ||
      typeof requestAnimationFrame === 'undefined' ||
      (typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches);
    if (reduced) {
      this.displayCount.set(target);
      return;
    }
    if (this.countRaf) cancelAnimationFrame(this.countRaf);
    const from = this.displayCount();
    const start = performance.now();
    const duration = 500;
    const tick = (now: number): void => {
      const t = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - t, 3);
      this.displayCount.set(Math.round(from + (target - from) * eased));
      if (t < 1) this.countRaf = requestAnimationFrame(tick);
    };
    this.countRaf = requestAnimationFrame(tick);
  }

  ngOnInit(): void {
    this.loadCategories();
    // Honor a ?category=<id> deep link (e.g. from the home "Shop by category" tiles).
    const categoryParam = this.route.snapshot.queryParamMap.get('category');
    const categoryId = categoryParam ? Number(categoryParam) : NaN;
    if (!isNaN(categoryId)) {
      this.selectCategory(categoryId);
    } else {
      this.loadAllProducts();
    }
  }

  protected onSearchChange(value: string): void {
    this.searchQuery.set(value);
    this.search$.next(value);
  }

  protected selectCategory(id: number | null): void {
    this.selectedCategoryId.set(id);
    this.loading.set(true);
    const source$ = id === null
      ? this.fetchProducts()
      : this.api.getProductsByCategory(id).pipe(catchError(() => of([])));

    source$.subscribe((products) => {
      this.allProducts.set(products);
      this.loading.set(false);
    });
  }

  private loadCategories(): void {
    this.api.getCategories().pipe(catchError(() => of([]))).subscribe((cats) => {
      this.categories.set(cats.filter((c) => c.isActive));
    });
  }

  private loadAllProducts(): void {
    this.loading.set(true);
    this.fetchProducts().subscribe((products) => {
      this.allProducts.set(products);
      this.store.seedApiProducts(products);
      this.loading.set(false);
    });
  }

  private fetchProducts() {
    return this.api.getProducts().pipe(catchError(() => of([])));
  }
}
