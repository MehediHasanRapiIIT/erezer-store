import { Component, computed, effect, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { SettingsStore } from '../core/store/settings.store';
import { RevealDirective } from '../core/reveal.directive';

/**
 * About Us. Every word and photo comes from Admin -> Settings -> About page;
 * a part left empty there is simply not shown.
 */
@Component({
  standalone: true,
  imports: [RouterLink, RevealDirective],
  template: `
    @if (about(); as a) {
      <article class="pb-16">
        <!-- Title and intro, over the main photo when there is one -->
        @if (a.heroImageUrl) {
          <header class="relative isolate overflow-hidden">
            <img [src]="a.heroImageUrl" alt="" class="absolute inset-0 -z-10 h-full w-full object-cover" />
            <div class="absolute inset-0 -z-10 bg-black/55"></div>
            <div class="mx-auto max-w-4xl px-4 py-24 text-center text-white sm:px-6 sm:py-32 lg:px-8">
              <h1 class="text-4xl font-semibold tracking-tight sm:text-5xl">{{ a.title }}</h1>
              @if (a.intro) {
                <p class="mx-auto mt-5 max-w-2xl whitespace-pre-line text-base text-white/85 sm:text-lg">{{ a.intro }}</p>
              }
            </div>
          </header>
        } @else {
          <header class="mx-auto max-w-3xl px-4 pb-6 pt-16 text-center sm:px-6 lg:px-8">
            <h1 class="app-section-title">{{ a.title }}</h1>
            @if (a.intro) {
              <p class="app-muted mt-4 whitespace-pre-line text-base sm:text-lg">{{ a.intro }}</p>
            }
          </header>
        }

        <!-- Sections, photo and text side by side, alternating -->
        <div class="mx-auto mt-14 max-w-6xl space-y-16 px-4 sm:px-6 lg:px-8">
          @for (s of a.sections ?? []; track $index; let odd = $odd) {
            @if (s.imageUrl) {
              <section class="grid items-center gap-8 md:grid-cols-2 md:gap-12" appReveal>
                <img [src]="s.imageUrl" [alt]="s.heading || ''" loading="lazy"
                  class="aspect-[4/3] w-full rounded-3xl object-cover" [class.md:order-2]="odd" />
                <div>
                  @if (s.heading) { <h2 class="text-2xl font-semibold tracking-tight sm:text-3xl">{{ s.heading }}</h2> }
                  @if (s.body) { <p class="app-muted mt-4 whitespace-pre-line leading-relaxed">{{ s.body }}</p> }
                </div>
              </section>
            } @else {
              <section class="mx-auto max-w-2xl text-center" appReveal>
                @if (s.heading) { <h2 class="text-2xl font-semibold tracking-tight sm:text-3xl">{{ s.heading }}</h2> }
                @if (s.body) { <p class="app-muted mt-4 whitespace-pre-line leading-relaxed">{{ s.body }}</p> }
              </section>
            }
          }
        </div>

        <!-- Closing button: a page of the shop, or another website in a new tab -->
        @if (a.ctaLabel && a.ctaLink) {
          <div class="mt-16 text-center">
            @if (isShopPage(a.ctaLink)) {
              <a [routerLink]="a.ctaLink" class="btn-primary inline-flex !rounded-full px-8">{{ a.ctaLabel }}</a>
            } @else {
              <a [href]="a.ctaLink" target="_blank" rel="noopener noreferrer" class="btn-primary inline-flex !rounded-full px-8">{{ a.ctaLabel }}</a>
            }
          </div>
        }
      </article>
    } @else {
      <div class="mx-auto max-w-4xl space-y-4 px-4 py-24" aria-busy="true">
        <div class="mx-auto h-10 w-2/3 animate-pulse rounded-xl bg-neutral-100 dark:bg-neutral-900"></div>
        <div class="mx-auto h-5 w-1/2 animate-pulse rounded-xl bg-neutral-100 dark:bg-neutral-900"></div>
      </div>
    }
  `,
})
export class AboutPage {
  private readonly settings = inject(SettingsStore);
  private readonly title = inject(Title);

  protected readonly about = computed(() => this.settings.settings()?.aboutPage ?? null);

  constructor() {
    effect(() => {
      const heading = this.about()?.title;
      this.title.setTitle(heading ? `${heading} | EREZER` : 'About | EREZER');
    });
  }

  /** "/shop" opens inside the shop; anything else is another website. */
  protected isShopPage(link: string): boolean {
    return link.startsWith('/') && !link.startsWith('//');
  }
}
