import { CurrencyPipe } from '@angular/common';
import { Component, computed, effect, ElementRef, HostListener, inject, input, output, signal, viewChild } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Subject, catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiService } from '../../core/api.service';
import { ApiBundleOffer, ApiCategory, ApiContentPage, ApiFlashSale, ApiProduct } from '../../core/api.models';
import { EcommerceStore } from '../../core/store/ecommerce.store';
import { SettingsStore } from '../../core/store/settings.store';
import { PixelService } from '../../core/pixel.service';

/** How many products show while typing; the rest are on the Shop page. */
const RESULTS = 6;
/** How many of anything else show in each group. */
const OTHER_RESULTS = 5;
const POPULAR_WORDS = 8;

/** One thing found that isn't a product: a category, an offer, a page or a store. */
interface Hit {
  title: string;
  /** A short line under the title, such as "Bundle" or the store's address. */
  note: string;
  /** Inside the shop… */
  route?: unknown[];
  query?: Record<string, unknown>;
  /** …or a page outside it (a store on Google Maps). */
  href?: string;
  /** Everything the words are looked for in. */
  text: string;
}

interface Group {
  key: string;
  title: string;
  hits: Hit[];
}

/** The shop's own pages, with the words people use for them. */
const SHOP_PAGES: { title: string; route: string; words: string }[] = [
  { title: 'Shop', route: '/shop', words: 'all products store buy new arrivals clothing' },
  { title: 'All Collections', route: '/categories', words: 'categories collections' },
  { title: 'Bundles', route: '/bundles', words: 'bundle offers deals combo package' },
  { title: 'Flash Sale', route: '/flash-sale', words: 'sale discount deals offer' },
  { title: 'Custom Design', route: '/custom-design', words: 'custom design your own print printing personalised t-shirt' },
  { title: 'Track Order', route: '/track-order', words: 'track tracking where is my order delivery status parcel courier shipping' },
  { title: 'Contact Us', route: '/contact', words: 'contact support help phone email message whatsapp call' },
  { title: 'About Us', route: '/about', words: 'about story company who we are brand' },
  { title: 'Wishlist', route: '/wishlist', words: 'wishlist favourites favorites saved heart' },
  { title: 'Cart', route: '/cart', words: 'cart bag basket' },
  { title: 'Checkout', route: '/checkout', words: 'checkout pay payment place order' },
  { title: 'My Account', route: '/account', words: 'account login log in sign in register sign up profile password address' },
  { title: 'My Orders', route: '/orders', words: 'orders order history invoice return exchange refund' },
];

/**
 * Search from any page: a panel that drops from the top with the search box,
 * the popular search words (the shop's main categories), and, as you type,
 * everything that matches: products, categories, offers, the shop's pages and
 * its stores. Enter, or "View all results", opens the Shop page on that search.
 * Unmounted while closed, like the other panels.
 */
@Component({
  selector: 'app-search-panel',
  standalone: true,
  imports: [CurrencyPipe, RouterLink],
  template: `
    @if (open()) {
      <div class="animate-overlay-in fixed inset-0 z-50 bg-black/50 backdrop-blur-md" (click)="close()" aria-hidden="true" data-testid="search-backdrop"></div>

      <section class="animate-overlay-in fixed inset-x-0 top-0 z-50 max-h-[100dvh] overflow-y-auto bg-white shadow-2xl dark:bg-neutral-950"
        role="dialog" aria-modal="true" aria-label="Search the shop" data-testid="search-panel">
        <div class="mx-auto w-full max-w-5xl px-4 py-4 sm:px-6 sm:py-5">
          <form class="flex items-center gap-3 sm:gap-6" (submit)="viewAll($event)">
            <a routerLink="/" (click)="close()" class="hidden shrink-0 sm:block" aria-label="Erezer – home">
              <span class="block aspect-[865/96] h-[17px] bg-current" role="img" aria-label="Erezer"
                style="-webkit-mask:url(logo-wordmark.png) left center/contain no-repeat;mask:url(logo-wordmark.png) left center/contain no-repeat"></span>
            </a>
            <div class="relative min-w-0 flex-1">
              <svg class="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-neutral-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-4.35-4.35M17 10.5a6.5 6.5 0 11-13 0 6.5 6.5 0 0113 0z" /></svg>
              <input #box type="search" [value]="text()" (input)="type($any($event.target).value)" placeholder="Search products…"
                autocomplete="off" enterkeyhint="search" aria-label="Search products" data-testid="search-input"
                class="w-full rounded-full border border-neutral-300 bg-neutral-50 py-2.5 pl-10 pr-4 text-base text-neutral-900 outline-none transition placeholder:text-neutral-400 focus:border-neutral-900 focus:bg-white focus:ring-1 focus:ring-neutral-900 dark:border-neutral-700 dark:bg-neutral-900 dark:text-white dark:focus:bg-neutral-900 dark:focus:border-white dark:focus:ring-white sm:text-sm" />
            </div>
            <button type="button" (click)="close()" data-testid="search-cancel"
              class="shrink-0 text-xs font-semibold uppercase tracking-[0.12em] text-neutral-600 transition hover:text-black dark:text-neutral-300 dark:hover:text-white">Cancel</button>
          </form>

          <div class="mx-auto max-w-3xl">
            <!-- Results as you type -->
            @if (searching()) {
              @if (loading() && results().length === 0 && groups().length === 0) {
                <p class="pt-5 text-sm text-neutral-500 dark:text-neutral-400">Searching…</p>
              } @else if (results().length === 0 && groups().length === 0) {
                <p class="pt-5 text-sm text-neutral-600 dark:text-neutral-300" data-testid="search-empty">
                  Nothing found for “{{ text().trim() }}”. Try another word.
                </p>
              }
              @if (results().length > 0) {
                <p class="pt-5 text-[11px] font-semibold uppercase tracking-[0.14em] text-neutral-400">Products</p>
                <ul class="grid grid-cols-1 gap-2 pt-2 sm:grid-cols-2" data-testid="search-results">
                  @for (p of results(); track p.id) {
                    <li>
                      <a [routerLink]="['/product', p.id]" (click)="close()" data-testid="search-result"
                        class="flex items-center gap-3 rounded-xl p-2 transition hover:bg-neutral-100 dark:hover:bg-neutral-900">
                        <img [src]="p.imageUrl" alt="" loading="lazy" class="h-14 w-14 shrink-0 rounded-lg bg-neutral-100 object-cover dark:bg-neutral-800" />
                        <span class="min-w-0">
                          <span class="block truncate text-sm font-medium">{{ p.name }}</span>
                          <span class="block text-sm tabular-nums text-neutral-500 dark:text-neutral-400">{{ priceOf(p) | currency:'BDT':'৳' }}</span>
                        </span>
                      </a>
                    </li>
                  }
                </ul>
                <button type="button" (click)="viewAll($event)" data-testid="search-view-all"
                  class="mt-3 text-sm font-semibold underline underline-offset-4 hover:no-underline">
                  View all products{{ total() > 0 ? ' (' + total() + ')' : '' }}
                </button>
              }

              @for (group of groups(); track group.key) {
                <p class="pt-5 text-[11px] font-semibold uppercase tracking-[0.14em] text-neutral-400">{{ group.title }}</p>
                <ul class="grid grid-cols-1 gap-1 pt-2 sm:grid-cols-2" [attr.data-testid]="'search-group-' + group.key">
                  @for (hit of group.hits; track hit.title + hit.note) {
                    <li>
                      @if (hit.href) {
                        <a [href]="hit.href" target="_blank" rel="noopener" (click)="close()" data-testid="search-hit"
                          class="block rounded-xl px-3 py-2 transition hover:bg-neutral-100 dark:hover:bg-neutral-900">
                          <span class="block truncate text-sm font-medium">{{ hit.title }}</span>
                          <span class="block truncate text-xs text-neutral-500 dark:text-neutral-400">{{ hit.note }}</span>
                        </a>
                      } @else {
                        <a [routerLink]="hit.route" [queryParams]="hit.query" (click)="close()" data-testid="search-hit"
                          class="block rounded-xl px-3 py-2 transition hover:bg-neutral-100 dark:hover:bg-neutral-900">
                          <span class="block truncate text-sm font-medium">{{ hit.title }}</span>
                          <span class="block truncate text-xs text-neutral-500 dark:text-neutral-400">{{ hit.note }}</span>
                        </a>
                      }
                    </li>
                  }
                </ul>
              }
            }

            <!-- Popular search words: the shop's main categories -->
            @if (popular().length > 0) {
              <p class="pt-5 text-[11px] font-semibold uppercase tracking-[0.14em] text-neutral-400">Popular search terms</p>
              <div class="flex flex-wrap gap-2 pt-2.5" data-testid="search-popular">
                @for (word of popular(); track word) {
                  <button type="button" (click)="pick(word)" data-testid="search-chip"
                    class="rounded-full border border-neutral-200 bg-neutral-50 px-3.5 py-1.5 text-sm text-neutral-800 transition hover:border-neutral-900 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-200 dark:hover:border-white">{{ word }}</button>
                }
              </div>
            }
          </div>
        </div>
      </section>
    }
  `,
})
export class SearchPanelComponent {
  private readonly api = inject(ApiService);
  private readonly store = inject(EcommerceStore);
  private readonly router = inject(Router);
  private readonly pixel = inject(PixelService);
  private readonly settings = inject(SettingsStore);

  readonly open = input(false);
  readonly closed = output<void>();

  protected readonly text = signal('');
  protected readonly results = signal<ApiProduct[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly popular = signal<string[]>([]);
  /** Two letters or more count as a search. */
  protected readonly searching = computed(() => this.text().trim().length >= 2);

  private readonly categories = signal<ApiCategory[]>([]);
  private readonly sales = signal<ApiFlashSale[]>([]);
  private readonly bundles = signal<ApiBundleOffer[]>([]);
  private readonly pages = signal<ApiContentPage[]>([]);

  /** Everything else the shop has, ready to look through without asking the server each time. */
  private readonly categoryHits = computed<Hit[]>(() => {
    const all = this.categories();
    const nameOf = (id: number | null | undefined) => all.find((c) => c.id === id)?.name;
    return all.map((c) => ({
      title: c.name,
      note: c.parentId != null ? `In ${nameOf(c.parentId) ?? 'Categories'}` : 'Category',
      route: c.slug ? ['/', c.slug] : ['/shop'],
      query: c.slug ? {} : { category: c.id },
      text: c.name,
    }));
  });

  private readonly offerHits = computed<Hit[]>(() => [
    ...this.sales().map((s) => ({
      title: s.name,
      note: this.upcoming(s) ? 'Flash Sale · coming soon' : 'Flash Sale',
      route: this.upcoming(s) ? ['/flash-sale'] : ['/flash-sale', s.id],
      text: [s.name, s.label, 'flash sale offer discount'].filter(Boolean).join(' '),
    })),
    ...this.bundles().map((o) => ({
      title: o.name,
      note: o.headline || 'Bundle',
      route: ['/bundles', o.id],
      text: [o.name, o.label, o.description, o.headline, 'bundle offer deal'].filter(Boolean).join(' '),
    })),
  ]);

  private readonly pageHits = computed<Hit[]>(() => [
    ...this.pages().map((p) => ({
      title: p.title,
      note: p.eyebrow || 'Page',
      route: ['/pages', p.slug],
      text: [p.title, p.eyebrow, p.intro, ...p.sections.flatMap((s) => [s.heading, s.body])].filter(Boolean).join(' '),
    })),
    ...SHOP_PAGES.map((p) => ({ title: p.title, note: 'Page', route: [p.route], text: `${p.title} ${p.words}` })),
  ]);

  private readonly storeHits = computed<Hit[]>(() =>
    (this.settings.settings()?.footer?.outlets ?? []).filter((o) => o.name || o.address).map((o) => ({
      title: o.name || 'Store',
      note: o.address || 'Store location',
      href: this.mapLink(o.mapUrl, o.name, o.address),
      text: [o.name, o.address, o.phone, 'store shop outlet location address map visit'].filter(Boolean).join(' '),
    })));

  /** What matches, group by group; a word in the name comes before a word further down. */
  protected readonly groups = computed<Group[]>(() => {
    const words = this.text().trim().toLowerCase().split(/\s+/).filter(Boolean);
    if (this.text().trim().length < 2) return [];
    const find = (hits: Hit[]) => hits
      .filter((h) => { const t = h.text.toLowerCase(); return words.every((w) => t.includes(w)); })
      .sort((x, y) => Number(words.every((w) => y.title.toLowerCase().includes(w))) - Number(words.every((w) => x.title.toLowerCase().includes(w))))
      .slice(0, OTHER_RESULTS);
    return [
      { key: 'categories', title: 'Categories', hits: find(this.categoryHits()) },
      { key: 'offers', title: 'Offers', hits: find(this.offerHits()) },
      { key: 'pages', title: 'Pages', hits: find(this.pageHits()) },
      { key: 'stores', title: 'Store locations', hits: find(this.storeHits()) },
    ].filter((g) => g.hits.length > 0);
  });

  private readonly box = viewChild<ElementRef<HTMLInputElement>>('box');
  private readonly typed$ = new Subject<string>();
  private contentLoaded = false;

  constructor() {
    this.typed$.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap((q) => {
        if (q.length < 2) return of(null);
        this.loading.set(true);
        return this.api.browseProducts({ q, page: 0, size: RESULTS }).pipe(catchError(() => of(null)));
      }),
      takeUntilDestroyed(),
    ).subscribe((page) => {
      this.loading.set(false);
      this.results.set(page?.content ?? []);
      this.total.set(page?.totalElements ?? 0);
    });

    effect(() => {
      if (!this.open()) return;
      // A fresh box each time, ready to type in.
      this.text.set('');
      this.results.set([]);
      this.total.set(0);
      this.typed$.next('');
      this.loadContent();
      queueMicrotask(() => this.box()?.nativeElement.focus());
    });
  }

  /** Categories, offers and pages: read once, the first time the panel opens. */
  private loadContent(): void {
    if (this.contentLoaded) return;
    this.contentLoaded = true;
    this.api.getCategories().pipe(catchError(() => of([] as ApiCategory[]))).subscribe((cats) => {
      const active = cats.filter((c) => c.isActive !== false);
      this.categories.set(active);
      const main = active.filter((c) => c.parentId == null).map((c) => c.name);
      this.popular.set([...new Set(main)].slice(0, POPULAR_WORDS));
    });
    this.api.getFlashSales().pipe(catchError(() => of([] as ApiFlashSale[]))).subscribe((s) => this.sales.set(s));
    this.api.getBundles().pipe(catchError(() => of([] as ApiBundleOffer[]))).subscribe((o) => this.bundles.set(o));
    this.api.getContentPages().pipe(catchError(() => of([] as ApiContentPage[]))).subscribe((p) => this.pages.set(p));
  }

  private upcoming(s: ApiFlashSale): boolean {
    return !!s.startsAt && new Date(s.startsAt).getTime() > Date.now();
  }

  /** The store's own Google Maps link, or a Maps search for its name and address. */
  private mapLink(url?: string | null, name?: string | null, address?: string | null): string | undefined {
    const own = (url ?? '').trim();
    if (/^https?:\/\/\S+$/i.test(own)) return own;
    const place = [name, address].map((t) => (t ?? '').trim()).filter(Boolean).join(', ');
    return place ? 'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent(place) : undefined;
  }

  protected type(value: string): void {
    this.text.set(value);
    this.typed$.next(value.trim());
  }

  protected pick(word: string): void {
    this.type(word);
    this.box()?.nativeElement.focus();
  }

  protected priceOf(p: ApiProduct): number {
    return this.store.toStoreProduct(p).price;
  }

  /** Enter, or "View all products": the Shop on this search; with no products, the first thing found. */
  protected viewAll(event: Event): void {
    event.preventDefault();
    const q = this.text().trim();
    if (!q) return;
    const first = this.groups()[0]?.hits[0];
    if (this.results().length === 0 && first) {
      this.close();
      if (first.href) window.open(first.href, '_blank', 'noopener');
      else void this.router.navigate(first.route ?? ['/'], { queryParams: first.query });
      return;
    }
    this.pixel.search(q);
    this.close();
    void this.router.navigate(['/shop'], { queryParams: { q } });
  }

  protected close(): void {
    this.closed.emit();
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.open()) this.close();
  }
}
