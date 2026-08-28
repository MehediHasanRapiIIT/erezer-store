import { isPlatformBrowser, NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, OnDestroy, OnInit, PLATFORM_ID, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ApiService } from '../core/api.service';
import { ApiBanner, ApiBrandStory, ApiCategory, ApiHighlight, ApiMarquee, ApiProduct } from '../core/api.models';
import { SettingsStore } from '../core/store/settings.store';
import { ProductCardComponent } from '../components/shared/product-card.component';
import { FlashSaleWidgetComponent } from '../components/shared/flash-sale-widget.component';
import { BundleWidgetComponent } from '../components/shared/bundle-widget.component';
import { RecentlyViewedComponent } from '../components/shared/recently-viewed.component';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { EcommerceStore } from '../core/store/ecommerce.store';
import { RevealDirective } from '../core/reveal.directive';
import { CountUpDirective } from '../core/count-up.directive';

@Component({
  standalone: true,
  imports: [RouterLink, ProductCardComponent, FlashSaleWidgetComponent, BundleWidgetComponent, FormsModule, RecentlyViewedComponent, TranslatePipe, RevealDirective, CountUpDirective, NgTemplateOutlet],
  template: `
    <!-- ── Cinematic hero ──────────────────────────────────────────────────── -->
    <section class="relative left-1/2 -mt-10 mb-16 h-[88svh] min-h-[34rem] w-screen -translate-x-1/2 overflow-hidden bg-black">
      <!-- Slides: crossfade + Ken Burns, with a parallax layer -->
      @for (banner of displayBanners(); track banner.id; let i = $index) {
        <div class="absolute inset-0 transition-opacity duration-[1200ms] ease-out"
          [class.opacity-100]="activeBanner() === i"
          [class.opacity-0]="activeBanner() !== i">
          <div class="absolute -inset-y-[8%] inset-x-0 will-change-transform"
            [style.transform]="'translate3d(0,' + parallax() + 'px,0)'">
            <img [src]="banner.image" [alt]="banner.title"
              class="hero-kenburns h-full w-full object-cover" />
          </div>
        </div>
      }

      <!-- Scrims for legible overlaid text -->
      <div class="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/80 via-black/35 to-black/10"></div>
      <div class="pointer-events-none absolute inset-0 bg-gradient-to-r from-black/40 to-transparent"></div>

      <!-- Text overlay — re-created on slide change so the animations re-run -->
      <div class="absolute inset-0 flex items-end">
        <div class="mx-auto w-full max-w-7xl px-6 pb-20 sm:px-8">
          @for (b of [activeBannerObj()]; track b.id) {
            <p class="hero-fade text-xs font-semibold uppercase tracking-[0.35em] text-white/80" style="animation-delay:.1s">
              {{ b.label }}
            </p>
            <h1 class="mt-4 max-w-4xl text-4xl font-semibold leading-[1.06] tracking-tight text-white sm:text-6xl md:text-7xl">
              @for (word of splitWords(b.title); track $index) {
                <span class="hero-word-mask mr-[0.22em]"><span class="hero-word"
                  [style.animation-delay]="(0.15 + $index * 0.07) + 's'">{{ word }}</span></span>
              }
            </h1>
            <p class="hero-fade mt-5 max-w-xl text-base text-white/85 sm:text-lg" style="animation-delay:.55s">
              {{ b.description }}
            </p>
            <div class="hero-fade mt-8 flex flex-wrap items-center gap-3" style="animation-delay:.7s">
              <a routerLink="/shop" class="rounded-full bg-white px-6 py-3 text-sm font-semibold text-black transition hover:bg-white/90">
                Shop the collection
              </a>
              <a routerLink="/shop" class="rounded-full border border-white/50 px-6 py-3 text-sm font-medium text-white backdrop-blur-sm transition hover:bg-white/10">
                New arrivals
              </a>
            </div>
          }
        </div>
      </div>

      <!-- Dots -->
      <div class="absolute bottom-6 left-1/2 flex -translate-x-1/2 gap-2">
        @for (banner of displayBanners(); track banner.id; let i = $index) {
          <button type="button"
            class="h-2 rounded-full transition-all duration-300"
            [class.w-8]="activeBanner() === i"
            [class.bg-white]="activeBanner() === i"
            [class.w-2]="activeBanner() !== i"
            [class.bg-white/50]="activeBanner() !== i"
            (click)="activeBanner.set(i)"
            [attr.aria-label]="'Go to banner ' + (i + 1)"></button>
        }
      </div>

      <!-- Scroll cue -->
      @if (!scrolled()) {
        <div class="scroll-cue absolute bottom-5 right-6 hidden flex-col items-center gap-1 text-white/80 sm:flex">
          <span class="text-[10px] uppercase tracking-[0.3em]">Scroll</span>
          <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
            <path stroke-linecap="round" stroke-linejoin="round" d="M19.5 8.25l-7.5 7.5-7.5-7.5" />
          </svg>
        </div>
      }
    </section>

    <!-- ── Flash sale promo (time-boxed; self-hides when none active) ───────── -->
    <div class="mb-16" appReveal>
      <app-flash-sale-widget />
    </div>

    <!-- ── Featured bundle promo (self-hides when none featured) ────────────── -->
    <div class="mb-16" appReveal>
      <app-bundle-widget />
    </div>

    <!-- ── Shop by category (lookbook) ─────────────────────────────────────── -->
    @if (collectionTiles().length > 0) {
      <section class="mb-16">
        <div class="mb-6 flex items-end justify-between" appReveal>
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.28em] text-neutral-500 dark:text-neutral-400">Collections</p>
            <h2 class="app-section-title mt-2 text-2xl">Shop by category</h2>
          </div>
          <a routerLink="/shop" class="text-sm font-medium underline underline-offset-4">Browse all</a>
        </div>

        <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          @for (tile of collectionTiles(); track tile.name; let i = $index) {
            <a [routerLink]="['/shop']" [queryParams]="tile.id ? { category: tile.id } : {}"
              class="group relative block aspect-[4/5] overflow-hidden rounded-2xl" [appReveal]="i">
              <img [src]="tile.image" [alt]="tile.name"
                class="h-full w-full object-cover transition-transform duration-[900ms] ease-out group-hover:scale-110" />
              <div class="absolute inset-0 bg-gradient-to-t from-black/75 via-black/15 to-transparent"></div>
              <div class="absolute inset-x-0 bottom-0 p-6">
                <p class="text-[11px] font-medium uppercase tracking-[0.22em] text-white/70">Collection</p>
                <h3 class="mt-1 text-2xl font-semibold tracking-tight text-white">{{ tile.name }}</h3>
                <span class="mt-3 inline-flex items-center gap-1.5 text-sm font-medium text-white">
                  Shop now
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 transition-transform duration-300 group-hover:translate-x-1"
                    fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5L21 12m0 0l-7.5 7.5M21 12H3" />
                  </svg>
                </span>
              </div>
            </a>
          }
        </div>
      </section>
    }

    <!-- ── Highlights (admin-managed) ──────────────────────────────────────── -->
    <section class="mb-14 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      @if (adminHighlights().length > 0) {
        @for (item of adminHighlights(); track $index; let i = $index) {
          <article class="app-card p-6 transition duration-300 hover:-translate-y-0.5 hover:shadow-lg hover:shadow-black/5" [appReveal]="i">
            <div class="mb-4 inline-flex h-11 w-11 items-center justify-center rounded-full bg-neutral-900 text-white dark:bg-white dark:text-black">
              <ng-container [ngTemplateOutlet]="highlightIcon" [ngTemplateOutletContext]="{ $implicit: item.icon }" />
            </div>
            <p class="text-3xl font-semibold tracking-tight">{{ item.value }}</p>
            <p class="mt-2 text-xs font-semibold uppercase tracking-[0.16em] text-neutral-500 dark:text-neutral-400">{{ item.label }}</p>
            <p class="mt-1 text-sm text-neutral-600 dark:text-neutral-400">{{ item.description }}</p>
          </article>
        }
      } @else {
        @for (item of highlights; track item.label; let i = $index) {
          <article class="app-card p-6 transition duration-300 hover:-translate-y-0.5 hover:shadow-lg hover:shadow-black/5" [appReveal]="i">
            <div class="mb-4 inline-flex h-11 w-11 items-center justify-center rounded-full bg-neutral-900 text-white dark:bg-white dark:text-black">
              <ng-container [ngTemplateOutlet]="highlightIcon" [ngTemplateOutletContext]="{ $implicit: item.icon }" />
            </div>
            <p class="text-3xl font-semibold tracking-tight">
              <span>{{ item.prefix }}</span><span [appCountUp]="item.target" [decimals]="item.decimals">0</span><span class="text-lg font-medium text-neutral-500 dark:text-neutral-400">{{ item.suffix }}</span>
            </p>
            <p class="mt-2 text-xs font-semibold uppercase tracking-[0.16em] text-neutral-500 dark:text-neutral-400">{{ item.label }}</p>
            <p class="mt-1 text-sm text-neutral-600 dark:text-neutral-400">{{ item.description }}</p>
          </article>
        }
      }
    </section>

    <!-- Shared highlight icon set -->
    <ng-template #highlightIcon let-icon>
      @switch (icon) {
        @case ('star') {
          <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 24 24" fill="currentColor"><path d="M12 17.27l5.18 3.04-1.37-5.88 4.56-3.95-6.01-.51L12 4.5l-2.36 5.47-6.01.51 4.56 3.95-1.37 5.88z"/></svg>
        }
        @case ('truck') {
          <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M8.25 18.75a1.5 1.5 0 11-3 0m3 0a1.5 1.5 0 00-3 0m3 0h6m-9 0H3.375a1.125 1.125 0 01-1.125-1.125V14.25m17.25 4.5a1.5 1.5 0 11-3 0m3 0a1.5 1.5 0 00-3 0m3 0h1.125c.621 0 1.129-.504 1.09-1.124a17.902 17.902 0 00-3.213-9.193 2.056 2.056 0 00-1.66-.84H14.25M16.5 18.75h-6V5.25A1.125 1.125 0 009.375 4.125H4.5"/></svg>
        }
        @case ('refresh') {
          <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M16.023 9.348h4.992V4.356M3.985 19.644v-4.992h4.992m-9.336-2.298A8.25 8.25 0 016.34 4.34m-2.32 8.32A8.25 8.25 0 0017.66 19.66M19.98 11.34A8.25 8.25 0 006.34 4.34"/></svg>
        }
        @case ('shield') {
          <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12c0 5.25-3.6 8.25-8.4 9.6a1.2 1.2 0 01-.6 0C7.2 20.25 3.6 17.25 3.6 12V6.3a1.2 1.2 0 01.75-1.11l7.2-2.7a1.2 1.2 0 01.9 0l7.2 2.7A1.2 1.2 0 0121 6.3z"/></svg>
        }
        @default {
          <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 24 24" fill="currentColor"><path d="M12 17.27l5.18 3.04-1.37-5.88 4.56-3.95-6.01-.51L12 4.5l-2.36 5.47-6.01.51 4.56 3.95-1.37 5.88z"/></svg>
        }
      }
    </ng-template>

    <!-- ── New arrivals (admin-managed) ────────────────────────────────────── -->
    @if (newArrivalProducts().length > 0) {
      <section class="mb-14 space-y-6">
        <div class="flex items-end justify-between" appReveal>
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.28em] text-neutral-500 dark:text-neutral-400">Just dropped</p>
            <h2 class="app-section-title mt-2 text-2xl">New arrivals</h2>
          </div>
          <a routerLink="/shop" class="text-sm font-medium underline underline-offset-4">View all products</a>
        </div>
        <div class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          @for (product of newArrivalProducts(); track product.id; let i = $index) {
            <app-product-card [product]="toStoreProduct(product)" [appReveal]="i" />
          }
        </div>
      </section>
    }

    <!-- ── Featured products ───────────────────────────────────────────────── -->
    <section class="space-y-6">
      <div class="flex items-end justify-between" appReveal>
        <h2 class="app-section-title text-2xl">Featured products</h2>
        <a routerLink="/shop" class="text-sm font-medium underline underline-offset-4">View all products</a>
      </div>

      @if (loading()) {
        <p class="app-muted">Loading products…</p>
      } @else {
        <div class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          @for (product of featuredApiProducts(); track product.id; let i = $index) {
            <app-product-card [product]="toStoreProduct(product)" [appReveal]="i" />
          } @empty {
            @for (product of store.featuredProducts(); track product.id; let i = $index) {
              <app-product-card [product]="product" [appReveal]="i" />
            }
          }
        </div>
      }
    </section>

    <!-- ── Marquee trust strip (admin-managed) ─────────────────────────────── -->
    @if (marquee().enabled && marquee().items.length > 0) {
      <section class="marquee-mask relative left-1/2 my-16 w-screen -translate-x-1/2 overflow-hidden bg-gradient-to-r from-neutral-950 via-neutral-800 to-neutral-950 py-4">
        <!-- soft edge fades -->
        <div class="pointer-events-none absolute inset-y-0 left-0 z-10 w-24 bg-gradient-to-r from-neutral-950 to-transparent"></div>
        <div class="pointer-events-none absolute inset-y-0 right-0 z-10 w-24 bg-gradient-to-l from-neutral-950 to-transparent"></div>
        <div class="marquee-track">
          @for (loop of marqueeLoop; track loop) {
            @for (item of marquee().items; track $index) {
              <span class="mx-8 inline-flex items-center gap-7 text-sm font-semibold uppercase tracking-[0.22em] text-white/90">
                {{ item }}
                <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5 text-amber-300/80" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 0l2.4 9.6L24 12l-9.6 2.4L12 24l-2.4-9.6L0 12l9.6-2.4z"/>
                </svg>
              </span>
            }
          }
        </div>
      </section>
    }

    <!-- ── Recently viewed (Phase 8) ───────────────────────────────────────── -->
    <section class="mt-14" appReveal>
      <app-recently-viewed [limit]="6" />
    </section>

    <!-- ── Brand story + lookbook gallery (admin-managed) ──────────────────── -->
    @if (brandStory(); as bs) {
      <section class="mt-16 grid items-center gap-10 lg:grid-cols-2">
        <div appReveal>
          @if (bs.eyebrow) {
            <p class="text-xs font-semibold uppercase tracking-[0.28em] text-neutral-500 dark:text-neutral-400">{{ bs.eyebrow }}</p>
          }
          <h2 class="app-section-title mt-3 text-3xl md:text-4xl">{{ bs.heading }}</h2>
          <p class="mt-4 max-w-md whitespace-pre-line text-neutral-600 dark:text-neutral-300">{{ bs.body }}</p>
          <div class="mt-6 flex items-center gap-4">
            @if (bs.ctaLabel) {
              @if (isInternal(bs.ctaLink)) {
                <a [routerLink]="bs.ctaLink" class="btn-primary">{{ bs.ctaLabel }}</a>
              } @else {
                <a [href]="bs.ctaLink" class="btn-primary">{{ bs.ctaLabel }}</a>
              }
            }
            @if (bs.socialHandle) {
              <a [href]="bs.socialUrl || null" target="_blank" rel="noopener"
                class="inline-flex items-center gap-1.5 text-sm font-medium text-neutral-500 transition hover:text-neutral-900 dark:text-neutral-400 dark:hover:text-neutral-100">
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="currentColor" viewBox="0 0 24 24"><path d="M12 2.2c3.2 0 3.6 0 4.85.07 1.17.05 1.8.25 2.23.42.56.22.96.48 1.38.9.42.42.68.82.9 1.38.17.42.37 1.06.42 2.23.06 1.27.07 1.65.07 4.85s0 3.58-.07 4.85c-.05 1.17-.25 1.8-.42 2.23-.22.56-.48.96-.9 1.38-.42.42-.82.68-1.38.9-.42.17-1.06.37-2.23.42-1.27.06-1.65.07-4.85.07s-3.58 0-4.85-.07c-1.17-.05-1.8-.25-2.23-.42a3.8 3.8 0 01-1.38-.9 3.8 3.8 0 01-.9-1.38c-.17-.42-.37-1.06-.42-2.23C2.21 15.58 2.2 15.2 2.2 12s0-3.58.07-4.85c.05-1.17.25-1.8.42-2.23.22-.56.48-.96.9-1.38.42-.42.82-.68 1.38-.9.42-.17 1.06-.37 2.23-.42C8.42 2.21 8.8 2.2 12 2.2zm0 3.04A6.76 6.76 0 1018.76 12 6.76 6.76 0 0012 5.24zm0 11.15A4.39 4.39 0 1116.39 12 4.39 4.39 0 0112 16.39zm6.96-11.45a1.58 1.58 0 11-1.58-1.58 1.58 1.58 0 011.58 1.58z"/></svg>
                {{ bs.socialHandle }}
              </a>
            }
          </div>
        </div>

        <div class="grid grid-cols-3 gap-3">
          @for (img of galleryImages(); track $index) {
            <a routerLink="/shop" class="group relative block aspect-square overflow-hidden rounded-xl" [appReveal]="$index">
              <img [src]="img" alt="Erezer lookbook"
                class="h-full w-full object-cover transition-transform duration-700 ease-out group-hover:scale-110" />
              <div class="pointer-events-none absolute inset-0 bg-black/0 transition-colors duration-300 group-hover:bg-black/20"></div>
            </a>
          }
        </div>
      </section>
    }

    <!-- ── Newsletter ──────────────────────────────────────────────────────── -->
    <section class="mt-14 app-card grid gap-6 p-8 md:grid-cols-2 md:items-center md:p-10" appReveal>
      <div>
        <p class="text-xs font-semibold uppercase tracking-[0.28em] text-neutral-500 dark:text-neutral-400">
          {{ 'home.newsletter.eyebrow' | t }}
        </p>
        <h2 class="mt-3 text-3xl font-semibold tracking-tight">{{ 'home.newsletter.headline' | t }}</h2>
        <p class="mt-3 max-w-xl text-neutral-600 dark:text-neutral-300">
          {{ 'home.newsletter.copy' | t }}
        </p>
      </div>
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
        <input
          type="email"
          [placeholder]="'home.newsletter.email_placeholder' | t"
          [(ngModel)]="newsletterEmail"
          [ngModelOptions]="{ standalone: true }"
          [disabled]="newsletterDone()"
          class="w-full rounded-full border border-neutral-300 bg-white px-4 py-3 text-sm outline-none transition focus:border-neutral-500 disabled:opacity-50 dark:border-neutral-700 dark:bg-neutral-900 dark:focus:border-neutral-500"
        />
        <button
          type="button"
          class="btn-primary"
          [disabled]="newsletterSubmitting() || newsletterDone() || !newsletterEmail.trim()"
          (click)="subscribeNewsletter()">
          {{ newsletterDone() ? ('home.newsletter.cta_done' | t) : newsletterSubmitting() ? ('home.newsletter.cta_busy' | t) : ('home.newsletter.cta' | t) }}
        </button>
      </div>
      @if (newsletterMessage()) {
        <p class="md:col-span-2 text-sm" [class.text-green-600]="!newsletterError()" [class.text-red-500]="newsletterError()">
          {{ newsletterMessage() }}
        </p>
      }
    </section>
  `
})
export class HomePage implements OnInit, OnDestroy {
  protected readonly store = inject(EcommerceStore);
  private readonly api = inject(ApiService);
  private readonly settings = inject(SettingsStore);

  private readonly platformId = inject(PLATFORM_ID);

  protected readonly activeBanner = signal(0);
  protected readonly loading = signal(false);
  protected readonly parallax = signal(0);
  protected readonly scrolled = signal(false);
  private sliderTimer: ReturnType<typeof setInterval> | null = null;
  private scrollTicking = false;
  protected toStoreProduct = this.store.toStoreProduct.bind(this.store);

  /** Current banner object — drives the hero text that re-animates on change. */
  protected readonly activeBannerObj = computed(() => {
    const banners = this.displayBanners();
    return banners[this.activeBanner()] ?? banners[0];
  });

  protected splitWords(text: string): string[] {
    return (text ?? '').split(/\s+/).filter(Boolean);
  }

  /** rAF-throttled parallax + scroll-cue toggle. */
  private readonly onScroll = (): void => {
    if (this.scrollTicking) return;
    this.scrollTicking = true;
    requestAnimationFrame(() => {
      const y = window.scrollY || 0;
      this.parallax.set(Math.min(y * 0.3, 160));
      this.scrolled.set(y > 40);
      this.scrollTicking = false;
    });
  };

  // ── Phase 6: newsletter signup ─────────────────────────────────────────────
  protected newsletterEmail = '';
  protected readonly newsletterSubmitting = signal(false);
  protected readonly newsletterDone       = signal(false);
  protected readonly newsletterError      = signal(false);
  protected readonly newsletterMessage    = signal<string>('');

  protected subscribeNewsletter(): void {
    const email = this.newsletterEmail.trim();
    if (!email) return;
    this.newsletterSubmitting.set(true);
    this.newsletterError.set(false);
    this.newsletterMessage.set('');
    this.api.subscribeNewsletter(email, 'STOREFRONT_HOME')
      .pipe(catchError((err) => {
        this.newsletterError.set(true);
        this.newsletterMessage.set(err?.error?.message ?? 'Could not subscribe. Please try again.');
        this.newsletterSubmitting.set(false);
        return of(null);
      }))
      .subscribe((response) => {
        this.newsletterSubmitting.set(false);
        if (response) {
          this.newsletterDone.set(true);
          this.newsletterMessage.set(response.message);
        }
      });
  }

  // API data signals
  private readonly homeData = signal<{
    banners: ApiBanner[];
    featuredItems: ApiProduct[];
    newArrivalItems: ApiProduct[];
  } | null>(null);
  protected readonly categories = signal<ApiCategory[]>([]);

  /** Curated editorial imagery for the category tiles (categories carry no image). */
  private readonly collectionImages = [
    'https://images.unsplash.com/photo-1490481651871-ab68de25d43d?auto=format&fit=crop&w=900&q=80',
    'https://images.unsplash.com/photo-1483985988355-763728e1935b?auto=format&fit=crop&w=900&q=80',
    'https://images.unsplash.com/photo-1539109136881-3be0616acf4b?auto=format&fit=crop&w=900&q=80',
    'https://images.unsplash.com/photo-1492707892479-7bc8d5a4ee93?auto=format&fit=crop&w=900&q=80',
    'https://images.unsplash.com/photo-1487222477894-8943e31ef7b2?auto=format&fit=crop&w=900&q=80',
    'https://images.unsplash.com/photo-1485968579580-b6d095142e6e?auto=format&fit=crop&w=900&q=80',
  ];

  /** Fallback trust-strip phrases (used until admin-managed settings load). */
  private readonly fallbackMarquee = [
    'Free shipping over ৳2000',
    'bKash accepted',
    '3-day easy exchange',
    'Premium materials',
    'Secure checkout',
    'Made for everyday',
  ];

  /** Admin-managed marquee (enabled flag + phrases). */
  protected readonly marquee = computed<ApiMarquee>(() => {
    const m = this.settings.settings()?.marquee;
    if (m && m.items && m.items.length > 0) return m;
    return { enabled: m?.enabled ?? true, items: this.fallbackMarquee };
  });

  /** Editorial lookbook imagery used only if the admin hasn't set gallery images. */
  protected readonly lookbook = [
    'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?auto=format&fit=crop&w=500&q=80',
    'https://images.unsplash.com/photo-1539109136881-3be0616acf4b?auto=format&fit=crop&w=500&q=80',
    'https://images.unsplash.com/photo-1503342217505-b0a15ec3261c?auto=format&fit=crop&w=500&q=80',
    'https://images.unsplash.com/photo-1485231183945-fffde7cc051e?auto=format&fit=crop&w=500&q=80',
    'https://images.unsplash.com/photo-1487412720507-e7ab37603c6f?auto=format&fit=crop&w=500&q=80',
    'https://images.unsplash.com/photo-1496747611176-843222e1e57c?auto=format&fit=crop&w=500&q=80',
  ];

  /** Admin-managed "Our story" content (falls back until settings load). */
  private readonly fallbackStory: ApiBrandStory = {
    eyebrow: 'Our story',
    heading: 'Considered clothing, made to last.',
    body: 'Erezer is built on timeless silhouettes, premium fabrics and honest pricing — '
      + 'pieces designed to live in your wardrobe for years, not seasons.',
    ctaLabel: 'Explore the collection',
    ctaLink: '/shop',
    socialHandle: '@erezer',
    socialUrl: 'https://instagram.com/erezer',
    images: [],
  };

  protected readonly brandStory = computed<ApiBrandStory>(() =>
    this.settings.settings()?.brandStory ?? this.fallbackStory);

  /** Admin-managed highlights band; empty falls back to the static count-up set. */
  protected readonly adminHighlights = computed<ApiHighlight[]>(() =>
    this.settings.settings()?.highlights ?? []);

  protected readonly galleryImages = computed<string[]>(() => {
    const imgs = this.brandStory().images;
    return imgs && imgs.length > 0 ? imgs : this.lookbook;
  });

  protected isInternal(url: string | null): boolean {
    return !!url && url.startsWith('/');
  }

  protected readonly marqueeLoop = [0, 1];

  private readonly staticCollections = [
    { id: null as number | null, name: 'New In',     image: this.collectionImages[0] },
    { id: null as number | null, name: 'Tops',       image: this.collectionImages[1] },
    { id: null as number | null, name: 'Essentials', image: this.collectionImages[2] },
  ];

  /** Up to 3 category tiles (real categories when available, else a curated fallback). */
  protected readonly collectionTiles = computed(() => {
    const cats = this.categories().filter((c) => c.isActive).slice(0, 3);
    if (cats.length === 0) return this.staticCollections;
    return cats.map((c, i) => ({
      id: c.id as number | null,
      name: c.name,
      // Prefer the admin-uploaded category image; fall back to curated editorial art.
      image: c.imageUrl?.trim() ? c.imageUrl : this.collectionImages[i % this.collectionImages.length],
    }));
  });

  // Normalised banners: prefer API data, fall back to static
  protected readonly displayBanners = computed(() => {
    const data = this.homeData();
    if (data && data.banners.length > 0) {
      return data.banners.map((b) => ({
        id: b.id,
        label: b.promotionTitle,
        title: b.promotionTitle,
        description: b.promotionDetails,
        image: b.imageUrl,
      }));
    }
    return this.staticBanners.map((b, i) => ({ id: String(i), ...b }));
  });

  protected readonly featuredApiProducts = computed(() => (this.homeData()?.featuredItems ?? []).slice(0, 9));
  protected readonly newArrivalProducts  = computed(() => (this.homeData()?.newArrivalItems ?? []).slice(0, 9));

  protected readonly highlights = [
    { icon: 'star',   prefix: '',   target: 4.9, decimals: 1, suffix: ' / 5',     label: 'Customer rating', description: 'Loved by shoppers across Bangladesh.' },
    { icon: 'truck',  prefix: '2–', target: 4,   decimals: 0, suffix: ' days',    label: 'Fast delivery',   description: 'Reliable nationwide shipping.' },
    { icon: 'refresh',prefix: '',   target: 3,   decimals: 0, suffix: '-day',     label: 'Easy exchanges',  description: 'Tell us within 3 days to return or exchange.' },
    { icon: 'shield', prefix: '',   target: 256, decimals: 0, suffix: '-bit SSL', label: 'Secure checkout', description: 'Protected payments, end to end.' },
  ] as const;

  private readonly staticBanners = [
    {
      label: 'New season',
      title: 'Minimal clothing, made to last.',
      description: 'EREZER blends timeless silhouettes with premium materials for the modern wardrobe.',
      image: 'https://images.unsplash.com/photo-1441986300917-64674bd600d8?auto=format&fit=crop&w=900&q=80',
    },
    {
      label: 'Urban essentials',
      title: 'Built for everyday movement.',
      description: 'Elevated basics designed for comfort, confidence, and all-day versatility.',
      image: 'https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=900&q=80',
    },
    {
      label: 'Signature edit',
      title: 'Refined layers, clean silhouettes.',
      description: 'Discover curated pieces that transition seamlessly from workday to weekend.',
      image: 'https://images.unsplash.com/photo-1469334031218-e382a71b716b?auto=format&fit=crop&w=900&q=80',
    }
  ] as const;

  ngOnInit(): void {
    this.loadHomeData();
    this.sliderTimer = setInterval(() => {
      this.activeBanner.update((v) => (v + 1) % Math.max(1, this.displayBanners().length));
    }, 6000);
    if (isPlatformBrowser(this.platformId)) {
      window.addEventListener('scroll', this.onScroll, { passive: true });
    }
  }

  ngOnDestroy(): void {
    if (this.sliderTimer) clearInterval(this.sliderTimer);
    if (isPlatformBrowser(this.platformId)) {
      window.removeEventListener('scroll', this.onScroll);
    }
  }

  private loadHomeData(): void {
    this.loading.set(true);
    this.api.getHomeData().pipe(
      catchError(() => of(null))
    ).subscribe((data) => {
      if (data) {
        this.homeData.set({
          banners: data.banners,
          featuredItems: data.featuredItems,
          newArrivalItems: data.newArrivalItems ?? [],
        });
        this.categories.set(data.categories ?? []);
        // also seed the store with all products from home data
        const all = [...data.popularItems, ...data.featuredItems, ...(data.newArrivalItems ?? [])];
        this.store.seedApiProducts(all);
      }
      this.loading.set(false);
    });
  }
}
