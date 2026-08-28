import { isPlatformBrowser, NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, OnDestroy, OnInit, PLATFORM_ID, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ApiService } from '../core/api.service';
import { ApiBanner, ApiBannerSlot, ApiHomeSection, ApiBrandStory, ApiCategory, ApiHighlight, ApiMarquee, ApiProduct } from '../core/api.models';
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
    <section class="relative hero-full-bleed full-bleed mb-16 h-[88svh] min-h-[34rem] overflow-hidden bg-black">
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
              <!-- Primary CTA comes from the banner when the admin set one,
                   otherwise the standing shop link. -->
              @if (b.ctaLabel && b.ctaLink) {
                <a [href]="b.ctaLink" (click)="onBandLink($event, b.ctaLink)" class="rounded-full bg-white px-6 py-3 text-sm font-semibold text-black transition hover:bg-white/90">
                  {{ b.ctaLabel }}
                </a>
              } @else {
                <a routerLink="/shop" class="rounded-full bg-white px-6 py-3 text-sm font-semibold text-black transition hover:bg-white/90">
                  Shop the collection
                </a>
              }
              <a routerLink="/custom-design" class="rounded-full border border-white/50 px-6 py-3 text-sm font-medium text-white backdrop-blur-sm transition hover:bg-white/10">
                Custom design
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

    <!-- ── Split band: two full-height category panels ─────────────────────── -->
    <!-- Renders nothing until an admin fills SPLIT_LEFT / SPLIT_RIGHT. -->
    @if (hasSplitBand()) {
      <section class="relative reveal-band full-bleed mb-16" appReveal>
        <div class="grid md:grid-cols-2">
          @for (panel of [splitLeft(), splitRight()]; track $index) {
            @if (panel) {
              <a [href]="panel.ctaLink || '/shop'" (click)="onBandLink($event, panel.ctaLink || '/shop')"
                class="group relative block h-[70svh] min-h-[26rem] overflow-hidden bg-neutral-900">
                <img [src]="panel.imageUrl" [alt]="panel.promotionTitle || ''"
                  class="h-full w-full object-cover transition-transform duration-[1200ms] ease-out group-hover:scale-105" />
                <span class="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/70 via-black/20 to-black/10"></span>
                <span class="absolute inset-0 flex flex-col items-center justify-center px-6 text-center">
                  @if (panel.promotionTitle) {
                    <span class="text-3xl font-semibold uppercase leading-tight tracking-[0.12em] text-white drop-shadow sm:text-5xl">
                      {{ panel.promotionTitle }}
                    </span>
                  }
                  @if (panel.promotionDetails) {
                    <span class="mt-3 max-w-sm text-sm text-white/80">{{ panel.promotionDetails }}</span>
                  }
                  @if (panel.ctaLabel) {
                    <span class="mt-8 rounded-full border border-white/70 px-7 py-3 text-xs font-semibold uppercase tracking-[0.18em] text-white backdrop-blur-sm transition group-hover:bg-white group-hover:text-black">
                      {{ panel.ctaLabel }}
                    </span>
                  }
                </span>
              </a>
            } @else {
              <!-- Only one half configured: keep the layout balanced. -->
              <div class="hidden bg-neutral-100 md:block dark:bg-neutral-900"></div>
            }
          }
        </div>
      </section>
    }

    <!-- ── Category tile grid (2x2) ─────────────────────────────────────────── -->
    @if (gridTiles().length) {
      <section class="relative reveal-band full-bleed mb-16" appReveal>
        <div class="grid grid-cols-1 sm:grid-cols-2">
          @for (tile of gridTiles(); track tile.id) {
            <a [href]="tile.ctaLink || '/shop'" (click)="onBandLink($event, tile.ctaLink || '/shop')"
              class="group relative block h-[42svh] min-h-[16rem] overflow-hidden bg-neutral-900">
              <img [src]="tile.imageUrl" [alt]="tile.promotionTitle || ''"
                class="h-full w-full object-cover transition-transform duration-[1200ms] ease-out group-hover:scale-105" />
              <span class="pointer-events-none absolute inset-0 bg-black/15 transition group-hover:bg-black/30"></span>
              <span class="absolute inset-0 flex items-center justify-center">
                <span class="rounded-full border border-white/80 bg-black/20 px-7 py-3 text-xs font-semibold uppercase tracking-[0.18em] text-white backdrop-blur-sm transition group-hover:bg-white group-hover:text-black">
                  {{ tile.ctaLabel || tile.promotionTitle }}
                </span>
              </span>
            </a>
          }
        </div>
      </section>
    }

    <!-- ── Custom-design promo: flat panel + full-bleed photo ───────────────── -->
    @if (customPromo(); as promo) {
      <section class="relative reveal-band full-bleed mb-16" appReveal>
        <div class="grid items-stretch md:grid-cols-2">
          <div class="flex flex-col justify-center bg-neutral-100 px-8 py-16 sm:px-14 dark:bg-neutral-900">
            <h2 class="text-3xl font-semibold uppercase leading-tight tracking-tight sm:text-4xl">
              {{ promo.promotionTitle || 'Customize your apparel, your way.' }}
            </h2>
            <p class="app-muted mt-5 max-w-md text-sm leading-relaxed sm:text-base">
              {{ promo.promotionDetails || 'Design your own t-shirts, hoodies and more in our online studio — no minimum order, even a single piece.' }}
            </p>
            <a [href]="promo.ctaLink || '/custom-design'" (click)="onBandLink($event, promo.ctaLink || '/custom-design')"
              class="mt-8 inline-flex w-fit items-center rounded-full bg-neutral-900 px-8 py-3.5 text-xs font-semibold uppercase tracking-[0.18em] text-white transition hover:bg-neutral-700 dark:bg-white dark:text-black dark:hover:bg-white/90">
              {{ promo.ctaLabel || 'Try it now' }}
            </a>
          </div>
          <div class="relative h-[46svh] min-h-[20rem] overflow-hidden md:h-auto">
            <img [src]="promo.imageUrl" [alt]="promo.promotionTitle || ''"
              class="h-full w-full object-cover" />
          </div>
        </div>
      </section>
    }

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
      <!-- Full-bleed: escapes the app shell's 1280px column so the collection
           tiles use the whole screen. Keeps the site's normal gutters so the
           cards do not butt against the viewport edge. -->
      <section class="relative  full-bleed mb-16">
        <div class="mb-6 flex items-end justify-between px-4 sm:px-6 lg:px-8" appReveal>
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.28em] text-neutral-500 dark:text-neutral-400">Collections</p>
            <h2 class="app-section-title mt-2 text-2xl">Shop by category</h2>
          </div>
          <a routerLink="/categories" class="text-sm font-medium underline underline-offset-4">Browse all</a>
        </div>

        <!-- No gap and square corners: the tiles butt together into one
             continuous band, edge to edge across the viewport. -->
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3">
          @for (tile of collectionTiles(); track tile.name; let i = $index) {
            <!-- Portrait aspect on small screens. On desktop the tiles are
                 ~630px wide, where an aspect ratio makes the height swing
                 wildly with viewport width, so fix the height and let the
                 image crop to fill. -->
            <!-- Straight to the category's own page when it has a slug; the
                 filtered shop is the fallback for a category created before
                 slugs existed. -->
            <a [routerLink]="tile.slug ? ['/', tile.slug] : ['/shop']"
              [queryParams]="tile.slug ? {} : (tile.id ? { category: tile.id } : {})"
              class="group relative block aspect-[4/5] overflow-hidden lg:aspect-auto lg:h-[36rem] xl:h-[42rem]" [appReveal]="i">
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
    <!-- Full-bleed too, so it lines up with the collection tiles above rather
         than stepping back into the narrower column. -->
    <section class="relative  full-bleed mb-14 px-4 sm:px-6 lg:px-8">
      <div class="card-grid-flush grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4">
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
      </div>
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
      <!-- Full-bleed like the bands above; keeps the site's gutters so the
           product cards do not sit flush against the viewport edge. -->
      <section class="relative  full-bleed mb-14 space-y-6 px-4 sm:px-6 lg:px-8">
        <div class="flex items-end justify-between" appReveal>
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.28em] text-neutral-500 dark:text-neutral-400">Just dropped</p>
            <h2 class="app-section-title mt-2 text-2xl">New arrivals</h2>
          </div>
          <a routerLink="/shop" class="text-sm font-medium underline underline-offset-4">View all products</a>
        </div>
        <div class="card-grid-flush grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5">
          @for (product of newArrivalProducts(); track product.id; let i = $index) {
            <app-product-card [product]="toStoreProduct(product)" [appReveal]="i" />
          }
        </div>
      </section>
    }

    <!-- ── Featured products ───────────────────────────────────────────────── -->
    <section class="relative  full-bleed space-y-6 px-4 sm:px-6 lg:px-8">
      <div class="flex items-end justify-between" appReveal>
        <h2 class="app-section-title text-2xl">Featured products</h2>
        <a routerLink="/shop" class="text-sm font-medium underline underline-offset-4">View all products</a>
      </div>

      @if (loading()) {
        <p class="app-muted">Loading products…</p>
      } @else {
        <div class="card-grid-flush grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5">
          @for (product of featuredApiProducts(); track product.id; let i = $index) {
            <app-product-card [product]="toStoreProduct(product)" [appReveal]="i" />
          } @empty {
            @for (product of fallbackFeatured(); track product.id; let i = $index) {
              <app-product-card [product]="product" [appReveal]="i" />
            }
          }
        </div>
      }
    </section>

    <!-- ── Admin-promoted category bands (e.g. Erezer Pink) ────────────────── -->
    <!-- One per category the admin flagged "show on home". Rendered from the
         same /app/home payload, so no extra request per section. -->
    @for (section of homeSections(); track section.categoryId) {
      <section class="relative full-bleed mb-14 space-y-6 px-4 sm:px-6 lg:px-8">
        <div class="flex items-end justify-between" appReveal>
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.28em] text-neutral-500 dark:text-neutral-400">Collection</p>
            <h2 class="mt-2 text-2xl font-semibold tracking-[-0.02em]">{{ section.name }}</h2>
          </div>
          @if (section.slug) {
            <a [routerLink]="['/', section.slug]" class="text-sm font-medium underline underline-offset-4">View all</a>
          }
        </div>
        <div class="card-grid-flush grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5">
          @for (product of section.products; track product.id; let i = $index) {
            <app-product-card [product]="toStoreProduct(product)" [appReveal]="i" />
          }
        </div>
      </section>
    }

    <!-- ── Marquee trust strip (admin-managed) ─────────────────────────────── -->
    @if (marquee().enabled && marquee().items.length > 0) {
      <section class="relative marquee-mask full-bleed my-16 overflow-hidden bg-gradient-to-r from-neutral-950 via-neutral-800 to-neutral-950 py-4">
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
      <!-- Editorial split: copy breathes on the left with generous padding,
           the mosaic runs off the right edge of the screen. The asymmetry is
           the point - a symmetric 50/50 with a uniform grid read as a form,
           not a lookbook. -->
      <section class="relative  full-bleed mt-16 overflow-hidden">
        <div class="grid items-center gap-12 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)] lg:gap-0">
          <div class="px-6 py-4 sm:px-10 lg:py-20 lg:pl-16 lg:pr-12 xl:pl-28" appReveal>
            @if (bs.eyebrow) {
              <p class="flex items-center gap-3 text-xs font-semibold uppercase tracking-[0.32em] text-neutral-500 dark:text-neutral-400">
                <span class="h-px w-10 bg-neutral-400 dark:bg-neutral-600"></span>
                {{ bs.eyebrow }}
              </p>
            }
            <h2 class="mt-5 max-w-xl text-4xl font-semibold leading-[1.08] tracking-[-0.02em] md:text-5xl xl:text-[3.5rem]">{{ bs.heading }}</h2>
            <p class="mt-6 max-w-lg whitespace-pre-line text-base leading-relaxed text-neutral-600 dark:text-neutral-300">{{ bs.body }}</p>
            <div class="mt-9 flex flex-wrap items-center gap-5">
              @if (bs.ctaLabel) {
                @if (isInternal(bs.ctaLink)) {
                  <a [routerLink]="bs.ctaLink" class="btn-primary">{{ bs.ctaLabel }}</a>
                } @else {
                  <a [href]="bs.ctaLink" class="btn-primary">{{ bs.ctaLabel }}</a>
                }
              }
              @if (bs.socialHandle) {
                <a [href]="bs.socialUrl || null" target="_blank" rel="noopener"
                  class="group inline-flex items-center gap-2 text-sm font-medium text-neutral-500 transition hover:text-neutral-900 dark:text-neutral-400 dark:hover:text-neutral-100">
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="currentColor" viewBox="0 0 24 24"><path d="M12 2.2c3.2 0 3.6 0 4.85.07 1.17.05 1.8.25 2.23.42.56.22.96.48 1.38.9.42.42.68.82.9 1.38.17.42.37 1.06.42 2.23.06 1.27.07 1.65.07 4.85s0 3.58-.07 4.85c-.05 1.17-.25 1.8-.42 2.23-.22.56-.48.96-.9 1.38-.42.42-.82.68-1.38.9-.42.17-1.06.37-2.23.42-1.27.06-1.65.07-4.85.07s-3.58 0-4.85-.07c-1.17-.05-1.8-.25-2.23-.42a3.8 3.8 0 01-1.38-.9 3.8 3.8 0 01-.9-1.38c-.17-.42-.37-1.06-.42-2.23C2.21 15.58 2.2 15.2 2.2 12s0-3.58.07-4.85c.05-1.17.25-1.8.42-2.23.22-.56.48-.96.9-1.38.42-.42.82-.68 1.38-.9.42-.17 1.06-.37 2.23-.42C8.42 2.21 8.8 2.2 12 2.2zm0 3.04A6.76 6.76 0 1018.76 12 6.76 6.76 0 0012 5.24zm0 11.15A4.39 4.39 0 1116.39 12 4.39 4.39 0 0112 16.39zm6.96-11.45a1.58 1.58 0 11-1.58-1.58 1.58 1.58 0 011.58 1.58z"/></svg>
                  <span class="underline-offset-4 group-hover:underline">{{ bs.socialHandle }}</span>
                </a>
              }
            </div>
          </div>

          <!-- Mosaic: the first image takes a 2x2 block so the grid has a focal
               point instead of six equal squares. -->
          <div class="grid grid-cols-3 grid-rows-3 lg:h-[38rem]">
            @for (img of galleryImages(); track $index) {
              <a routerLink="/shop"
                class="group relative block aspect-square overflow-hidden lg:aspect-auto"
                [class.col-span-2]="$index === 0"
                [class.row-span-2]="$index === 0"
                [appReveal]="$index">
                <img [src]="img" alt="Erezer lookbook"
                  class="h-full w-full object-cover transition-transform duration-[900ms] ease-out group-hover:scale-[1.07]" />
                <div class="pointer-events-none absolute inset-0 bg-black/0 transition-colors duration-300 group-hover:bg-black/25"></div>
                <span class="pointer-events-none absolute inset-x-0 bottom-0 flex translate-y-2 items-center gap-1.5 p-4 text-xs font-semibold uppercase tracking-[0.18em] text-white opacity-0 transition-all duration-300 group-hover:translate-y-0 group-hover:opacity-100">
                  Shop
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5L21 12m0 0l-7.5 7.5M21 12H3" />
                  </svg>
                </span>
              </a>
            }
          </div>
        </div>
      </section>
    }

    <!-- ── Newsletter ──────────────────────────────────────────────────────── -->
    <!-- Full-bleed and square-cornered so it reads as a band rather than a
         floating card sitting in a narrower column. -->
    <section class="relative app-card full-bleed mt-14 grid gap-6 rounded-none border-x-0 p-8 md:grid-cols-2 md:items-center md:p-10 lg:px-12" appReveal>
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
  private readonly router = inject(Router);

  /**
   * Keeps admin-authored band links as real hrefs while still navigating like a
   * SPA.
   *
   * The link is free text an admin typed, so it can be an internal path or an
   * external URL. Leaving it as a plain href reloads the whole app on every
   * category click; using routerLink alone breaks external links and loses
   * middle-click / "open in new tab" / crawlable markup. So the href stays, and
   * a plain left-click on an internal path is intercepted and routed instead.
   */
  protected onBandLink(event: MouseEvent, link: string | null | undefined): void {
    if (!link || /^(https?:)?\/\//i.test(link) || link.startsWith('mailto:')) return;
    // Let the browser own modified clicks - they mean "open elsewhere".
    if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || event.button !== 0) return;
    event.preventDefault();
    void this.router.navigateByUrl(link);
  }


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
    homeSections?: ApiHomeSection[];
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

  // Same shape as a real tile (slug included) so the template binding holds
  // whichever branch collectionTiles() returns.
  private readonly staticCollections = [
    { id: null as number | null, slug: null as string | null, name: 'New In',     image: this.collectionImages[0] },
    { id: null as number | null, slug: null as string | null, name: 'Tops',       image: this.collectionImages[1] },
    { id: null as number | null, slug: null as string | null, name: 'Essentials', image: this.collectionImages[2] },
  ];

  /** Up to 6 category tiles (real categories when available, else a curated fallback). */
  protected readonly collectionTiles = computed(() => {
    // Six on the landing page; the rest are reachable via "Browse all".
    const cats = this.categories().filter((c) => c.isActive).slice(0, 6);
    if (cats.length === 0) return this.staticCollections;
    return cats.map((c, i) => ({
      id: c.id as number | null,
      name: c.name,
      slug: c.slug ?? null,
      // Prefer the admin-uploaded category image; fall back to curated editorial art.
      image: c.imageUrl?.trim() ? c.imageUrl : this.collectionImages[i % this.collectionImages.length],
    }));
  });

  /**
   * Banners grouped by the landing-page band they fill.
   *
   * /app/home returns every live banner in one payload, so the bands are split
   * here rather than issuing a request per band. A banner with no slot is
   * treated as HERO - that is where banners lived before the editorial bands
   * existed, so legacy rows keep behaving exactly as they did.
   */
  private readonly bannersBySlot = computed(() => {
    const grouped = new Map<ApiBannerSlot, ApiBanner[]>();
    for (const b of this.homeData()?.banners ?? []) {
      const slot = b.slot ?? 'HERO';
      const list = grouped.get(slot) ?? [];
      list.push(b);
      grouped.set(slot, list);
    }
    return grouped;
  });

  /** Banners for one band, or an empty array — every band self-hides when empty. */
  private bannersFor(slot: ApiBannerSlot): ApiBanner[] {
    return this.bannersBySlot().get(slot) ?? [];
  }

  /** First banner of a single-image band, or null. */
  private bannerFor(slot: ApiBannerSlot): ApiBanner | null {
    return this.bannersFor(slot)[0] ?? null;
  }

  // Hero: admin-managed HERO banners, falling back to the curated static set so
  // the top of the page is never blank on a fresh install.
  protected readonly displayBanners = computed(() => {
    const hero = this.bannersFor('HERO');
    if (hero.length > 0) {
      return hero.map((b) => ({
        id: b.id,
        label: b.promotionTitle,
        title: b.promotionTitle,
        description: b.promotionDetails,
        image: b.imageUrl,
        ctaLabel: b.ctaLabel,
        ctaLink: b.ctaLink,
      }));
    }
    return this.staticBanners.map((b, i) => ({
      id: String(i), ...b, ctaLabel: null as string | null, ctaLink: null as string | null,
    }));
  });

  // ── Editorial bands ────────────────────────────────────────────────────────
  // Each is null/empty until an admin uploads a banner for that slot, and the
  // matching section renders nothing rather than an empty frame.
  protected readonly splitLeft   = computed(() => this.bannerFor('SPLIT_LEFT'));
  protected readonly splitRight  = computed(() => this.bannerFor('SPLIT_RIGHT'));
  protected readonly customPromo = computed(() => this.bannerFor('CUSTOM_PROMO'));

  /** The 2x2 tiles, in reading order, skipping any slot with no banner. */
  protected readonly gridTiles = computed(() =>
    (['GRID_1', 'GRID_2', 'GRID_3', 'GRID_4'] as ApiBannerSlot[])
      .map((slot) => this.bannerFor(slot))
      .filter((b): b is ApiBanner => b !== null));

  /** True when at least one half of the split band has an image. */
  protected readonly hasSplitBand = computed(() => !!this.splitLeft() || !!this.splitRight());

  // Five each - one full row at the widest breakpoint. "View all products"
  // links through to the full catalogue.
  protected readonly featuredApiProducts = computed(() => (this.homeData()?.featuredItems ?? []).slice(0, 5));
  /**
   * Admin-promoted category bands, e.g. "Erezer Pink". Each renders below
   * Featured products with its own products and a link to its own page.
   * Empty ones are already filtered out server-side.
   */
  protected readonly homeSections = computed(() => this.homeData()?.homeSections ?? []);

  /** Static fallback shown only when the API returns no featured items. */
  protected readonly fallbackFeatured = computed(() => this.store.featuredProducts().slice(0, 5));
  protected readonly newArrivalProducts  = computed(() => (this.homeData()?.newArrivalItems ?? []).slice(0, 5));

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
          homeSections: data.homeSections ?? [],
        });
        this.categories.set(data.categories ?? []);
        // also seed the store with all products from home data
        const all = [
          ...data.popularItems,
          ...data.featuredItems,
          ...(data.newArrivalItems ?? []),
          // Promoted-category products too, so their cards resolve like any other.
          ...(data.homeSections ?? []).flatMap((s) => s.products),
        ];
        this.store.seedApiProducts(all);
      }
      this.loading.set(false);
    });
  }
}
