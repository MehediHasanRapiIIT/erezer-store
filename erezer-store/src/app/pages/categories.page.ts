import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ApiService } from '../core/api.service';
import { ApiCategory } from '../core/api.models';
import { RevealDirective } from '../core/reveal.directive';
import { SeoService } from '../core/seo.service';

/** Categories revealed per click of "View more". */
const PAGE_SIZE = 9;

/**
 * Every collection, in one place — reached from the home page's "Browse all".
 *
 * Shows nine at a time and grows in nine-category steps rather than paginating.
 * A shop has few enough categories that they all arrive in one request, so the
 * button only reveals what is already loaded; there is no extra round trip and
 * no page reload to lose the reader's place.
 */
@Component({
  selector: 'app-categories-page',
  standalone: true,
  imports: [RouterLink, RevealDirective],
  template: `
    <section class="relative full-bleed mb-8 border-b border-neutral-200 px-4 pb-8 sm:px-6 lg:px-8 dark:border-neutral-800" appReveal>
      <p class="flex items-center gap-3 text-xs font-semibold uppercase tracking-[0.32em] text-neutral-500 dark:text-neutral-400">
        <span class="h-px w-10 bg-neutral-400 dark:bg-neutral-600"></span>
        Collections
      </p>
      <!-- Size set directly: app-section-title hardcodes 1.875rem and ties with
           Tailwind's text-* utilities on specificity. -->
      <h1 class="mt-4 text-4xl font-semibold leading-[1.05] tracking-[-0.02em] md:text-5xl xl:text-6xl">Shop by category</h1>
      <p class="app-muted mt-4 text-base">
        {{ loading() ? 'Loading…' : (total() + ' ' + (total() === 1 ? 'collection' : 'collections')) }}
      </p>
    </section>

    @if (!loading() && total() === 0) {
      <div class="py-20 text-center">
        <p class="app-muted text-sm">No collections yet.</p>
        <a routerLink="/shop" class="btn-primary mt-6 inline-flex">Browse the shop</a>
      </div>
    } @else {
      <!-- Same treatment as the home page's collection band: edge to edge, no
           gaps, square corners. -->
      <section class="relative full-bleed grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3">
        @for (cat of visible(); track cat.id; let i = $index) {
          <a [routerLink]="cat.slug ? ['/', cat.slug] : ['/shop']"
            [queryParams]="cat.slug ? {} : { category: cat.id }"
            class="group relative block aspect-[4/5] overflow-hidden lg:aspect-auto lg:h-[34rem] xl:h-[38rem]"
            [appReveal]="i % 9">
            <img [src]="imageFor(cat, i)" [alt]="cat.name"
              class="h-full w-full object-cover transition-transform duration-[900ms] ease-out group-hover:scale-105" />
            <span class="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/75 via-black/15 to-transparent"></span>
            <span class="absolute inset-x-0 bottom-0 p-6">
              <span class="block text-[11px] font-medium uppercase tracking-[0.22em] text-white/70">Collection</span>
              <span class="mt-1 block text-2xl font-semibold tracking-tight text-white">{{ cat.name }}</span>
              <span class="mt-3 inline-flex items-center gap-1.5 text-sm font-medium text-white">
                Shop now
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 transition-transform duration-300 group-hover:translate-x-1"
                  fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5L21 12m0 0l-7.5 7.5M21 12H3" />
                </svg>
              </span>
            </span>
          </a>
        }
      </section>

      @if (hasMore()) {
        <div class="py-12 text-center">
          <button type="button" (click)="showMore()" class="btn-primary px-8 py-3">
            View more ({{ remaining() }})
          </button>
        </div>
      }
    }
  `,
})
export class CategoriesPage {
  private readonly api = inject(ApiService);
  private readonly seo = inject(SeoService);

  private readonly categories = signal<ApiCategory[]>([]);
  protected readonly loading = signal(true);
  private readonly shown = signal(PAGE_SIZE);

  protected readonly total = computed(() => this.categories().length);
  protected readonly visible = computed(() => this.categories().slice(0, this.shown()));
  protected readonly remaining = computed(() => Math.max(0, this.total() - this.shown()));
  protected readonly hasMore = computed(() => this.remaining() > 0);

  /**
   * Curated stand-ins for categories with no uploaded image, so the grid never
   * shows an empty tile. Indexed so neighbouring tiles do not repeat.
   */
  private readonly fallbackImages = [
    'https://images.unsplash.com/photo-1490481651871-ab68de25d43d?auto=format&fit=crop&w=900&q=80',
    'https://images.unsplash.com/photo-1483985988355-763728e1935b?auto=format&fit=crop&w=900&q=80',
    'https://images.unsplash.com/photo-1539109136881-3be0616acf4b?auto=format&fit=crop&w=900&q=80',
    'https://images.unsplash.com/photo-1492707892479-7bc8d5a4ee93?auto=format&fit=crop&w=900&q=80',
    'https://images.unsplash.com/photo-1487222477894-8943e31ef7b2?auto=format&fit=crop&w=900&q=80',
    'https://images.unsplash.com/photo-1485968579580-b6d095142e6e?auto=format&fit=crop&w=900&q=80',
  ];

  constructor() {
    this.seo.update({
      title: 'Collections',
      description: 'Browse every EREZER collection.',
    });

    this.api.getCategories().pipe(catchError(() => of([] as ApiCategory[]))).subscribe((cats) => {
      this.categories.set(cats.filter((c) => c.isActive !== false));
      this.loading.set(false);
    });
  }

  protected imageFor(cat: ApiCategory, index: number): string {
    return cat.imageUrl?.trim()
      ? cat.imageUrl
      : this.fallbackImages[index % this.fallbackImages.length];
  }

  protected showMore(): void {
    this.shown.update((n) => n + PAGE_SIZE);
  }
}
