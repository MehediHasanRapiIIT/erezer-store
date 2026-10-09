import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ApiService } from '../core/api.service';
import type { ApiContentPage } from '../core/api.models';
import { RevealDirective } from '../core/reveal.directive';
import { SeoService } from '../core/seo.service';

/**
 * One of the shop's own pages - "Our Mission", "Our Values" - written in the
 * admin panel and read at /pages/<slug>.
 *
 * Laid out like a magazine page rather than a wall of text: a dark title band,
 * the opening set large, the sections as a numbered list, and the closing line
 * on a band of its own.
 */
@Component({
  standalone: true,
  imports: [RouterLink, RevealDirective],
  template: `
    @if (page(); as p) {
      <article class="content-page pb-4" data-testid="content-page">
        <!-- Title band: black, or the page's own picture under a dark veil -->
        <header class="content-hero full-bleed relative isolate overflow-hidden bg-neutral-950 text-white">
          @if (p.heroImageUrl) {
            <img [src]="p.heroImageUrl" alt="" class="absolute inset-0 -z-10 h-full w-full object-cover" />
            <div class="absolute inset-0 -z-10 bg-black/60"></div>
          } @else {
            <!-- A quiet pattern so a band with no picture is not a flat block -->
            <div class="content-hero-glow pointer-events-none absolute inset-0 -z-10" aria-hidden="true"></div>
          }
          <div class="mx-auto max-w-5xl px-5 py-20 sm:px-8 sm:py-28 lg:py-36">
            @if (p.eyebrow) {
              <p class="hero-fade flex items-center gap-3 text-[11px] font-semibold uppercase tracking-[0.4em] text-white/70" data-testid="content-eyebrow">
                <span class="h-px w-10 bg-white/40"></span>{{ p.eyebrow }}
              </p>
            }
            <h1 class="hero-fade mt-5 text-5xl font-semibold leading-[1.02] tracking-[-0.03em] sm:text-7xl lg:text-8xl" style="animation-delay:.08s" data-testid="content-title">
              {{ p.title }}
            </h1>
            @if (lead()) {
              <p class="hero-fade mt-8 max-w-3xl text-lg leading-relaxed text-white/85 sm:text-2xl sm:leading-snug" style="animation-delay:.18s" data-testid="content-lead">
                {{ lead() }}
              </p>
            }
          </div>
        </header>

        <!-- The rest of the opening -->
        @if (introRest().length > 0) {
          <div class="mx-auto max-w-3xl px-5 pt-14 sm:px-8 sm:pt-20" appReveal>
            @for (para of introRest(); track $index) {
              <p class="mt-5 text-lg leading-relaxed text-neutral-700 first:mt-0 dark:text-neutral-300 sm:text-xl" data-testid="content-intro">{{ para }}</p>
            }
          </div>
        }

        <!-- Sections: a numbered list, each with room to breathe -->
        @if ((p.sections ?? []).length > 0) {
          <div class="mx-auto mt-14 max-w-5xl px-5 sm:mt-20 sm:px-8" data-testid="content-sections">
            @for (s of p.sections; track $index; let i = $index) {
              <section class="content-section grid gap-x-10 gap-y-3 border-t border-neutral-200 py-9 dark:border-neutral-800 sm:py-12 md:grid-cols-[7rem_minmax(0,1fr)]"
                [class.md:grid-cols-[7rem_minmax(0,1fr)_minmax(0,18rem)]]="!!s.imageUrl" [appReveal]="i % 4" data-testid="content-section">
                <p class="content-number select-none text-4xl font-semibold tabular-nums leading-none tracking-tight text-neutral-300 dark:text-neutral-700 sm:text-5xl" aria-hidden="true">
                  {{ number(i) }}
                </p>
                <div>
                  @if (s.heading) {
                    <h2 class="text-2xl font-semibold leading-tight tracking-tight sm:text-3xl">{{ s.heading }}</h2>
                  }
                  @if (s.body) {
                    <p class="mt-3 max-w-2xl whitespace-pre-line text-base leading-relaxed text-neutral-600 dark:text-neutral-400 sm:text-lg">{{ s.body }}</p>
                  }
                </div>
                @if (s.imageUrl) {
                  <img [src]="s.imageUrl" [alt]="s.heading || ''" loading="lazy" class="aspect-[4/3] w-full rounded-2xl object-cover md:aspect-square" />
                }
              </section>
            }
            <div class="border-t border-neutral-200 dark:border-neutral-800"></div>
          </div>
        }

        <!-- The closing line, on a band of its own -->
        @if (p.closing || (p.ctaLabel && p.ctaLink)) {
          <div class="content-closing full-bleed mt-16 bg-neutral-950 text-white sm:mt-24" appReveal data-testid="content-closing">
            <div class="mx-auto max-w-4xl px-5 py-16 text-center sm:px-8 sm:py-24">
              @if (p.closing) {
                <p class="whitespace-pre-line text-3xl font-semibold leading-tight tracking-[-0.02em] sm:text-5xl">{{ p.closing }}</p>
              }
              @if (p.ctaLabel && p.ctaLink) {
                @if (isShopPage(p.ctaLink)) {
                  <a [routerLink]="p.ctaLink" class="mt-9 inline-flex rounded-full bg-white px-8 py-3 text-sm font-semibold text-black transition hover:bg-white/90">{{ p.ctaLabel }}</a>
                } @else {
                  <a [href]="p.ctaLink" target="_blank" rel="noopener noreferrer" class="mt-9 inline-flex rounded-full bg-white px-8 py-3 text-sm font-semibold text-black transition hover:bg-white/90">{{ p.ctaLabel }}</a>
                }
              }
            </div>
          </div>
        }

        <!-- The shop's other pages, to read next -->
        @if (others().length > 0) {
          <nav class="mx-auto max-w-5xl px-5 pt-12 sm:px-8 sm:pt-16" aria-label="More about us" data-testid="content-others">
            <p class="text-[11px] font-semibold uppercase tracking-[0.32em] text-neutral-500 dark:text-neutral-400">Keep reading</p>
            <div class="mt-4 grid gap-3 sm:grid-cols-2">
              @for (o of others(); track o.slug) {
                <a [routerLink]="['/pages', o.slug]"
                  class="group flex items-center justify-between gap-4 rounded-2xl border border-neutral-200 px-5 py-5 transition hover:border-neutral-900 dark:border-neutral-800 dark:hover:border-white">
                  <span class="text-xl font-semibold tracking-tight sm:text-2xl">{{ o.title }}</span>
                  <svg class="h-5 w-5 shrink-0 transition-transform duration-300 group-hover:translate-x-1" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5L21 12m0 0l-7.5 7.5M21 12H3" />
                  </svg>
                </a>
              }
            </div>
          </nav>
        }
      </article>
    } @else if (missing()) {
      <div class="mx-auto max-w-xl px-4 py-28 text-center">
        <h1 class="app-section-title">This page isn't here</h1>
        <p class="app-muted mt-3 text-sm">It may have been moved or taken down.</p>
        <a routerLink="/" class="btn-primary mt-6 inline-flex !rounded-full px-6">Back to the shop</a>
      </div>
    } @else {
      <div class="mx-auto max-w-4xl space-y-4 px-4 py-24" aria-busy="true">
        <div class="h-12 w-2/3 animate-pulse rounded-xl bg-neutral-100 dark:bg-neutral-900"></div>
        <div class="h-5 w-1/2 animate-pulse rounded-xl bg-neutral-100 dark:bg-neutral-900"></div>
      </div>
    }
  `,
  styles: [`
    /* The page starts under the site header, with the app shell's top padding
       taken back so the band meets the header instead of floating below it. */
    .content-hero { margin-top: -2.5rem; }
    .content-hero-glow {
      background:
        radial-gradient(60rem 28rem at 85% -10%, rgb(255 255 255 / 0.10), transparent 60%),
        radial-gradient(40rem 22rem at -5% 110%, rgb(255 255 255 / 0.07), transparent 60%),
        repeating-linear-gradient(90deg, rgb(255 255 255 / 0.035) 0 1px, transparent 1px 120px);
    }
    /* The band reads as a band in the dark theme too, where the page is black already. */
    :host-context(.dark) .content-hero,
    :host-context(.dark) .content-closing { background: rgb(23 23 23); }
    /* The number is decoration: on a phone it sits above the heading, smaller. */
    @media (max-width: 767px) {
      .content-number { font-size: 1.5rem; letter-spacing: 0.02em; }
    }
  `],
})
export class ContentPage {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly seo = inject(SeoService);

  protected readonly page = signal<ApiContentPage | null>(null);
  protected readonly missing = signal(false);
  private readonly all = signal<ApiContentPage[]>([]);

  /** The opening's paragraphs: the first is set large in the title band, the rest follow below it. */
  private readonly paragraphs = computed(() =>
    (this.page()?.intro ?? '').split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean));
  protected readonly lead = computed(() => this.paragraphs()[0] ?? '');
  protected readonly introRest = computed(() => this.paragraphs().slice(1));
  /** The other pages of the same kind, to offer at the foot. */
  protected readonly others = computed(() => this.all().filter((p) => p.slug !== this.page()?.slug));

  constructor() {
    // The same component serves every page: load again whenever the address changes.
    this.route.paramMap.subscribe((params) => this.load(params.get('slug') ?? ''));
    this.api.getContentPages().pipe(catchError(() => of([] as ApiContentPage[]))).subscribe((list) => this.all.set(list));
  }

  private load(slug: string): void {
    this.page.set(null);
    this.missing.set(false);
    this.api.getContentPage(slug).pipe(catchError(() => of(null))).subscribe((p) => {
      if (!p) {
        this.missing.set(true);
        return;
      }
      this.page.set(p);
      this.seo.update({
        title: p.title,
        description: (p.intro ?? '').split(/\n\s*\n/)[0] || p.closing || undefined,
        image: p.heroImageUrl || undefined,
        url: `${location.origin}/pages/${p.slug}`,
        type: 'article',
      });
      if (typeof window !== 'undefined') window.scrollTo({ top: 0 });
    });
  }

  /** "01", "02": the section's place in the list. */
  protected number(index: number): string {
    return String(index + 1).padStart(2, '0');
  }

  /** "/shop" opens inside the shop; anything else is another website. */
  protected isShopPage(link: string): boolean {
    return link.startsWith('/');
  }
}
