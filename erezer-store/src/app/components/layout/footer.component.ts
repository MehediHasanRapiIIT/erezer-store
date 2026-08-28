import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiFooter, ApiFooterOutlet, ApiFooterPromise } from '../../core/api.models';
import { SettingsStore } from '../../core/store/settings.store';

/** Static fallback used until the admin-managed footer loads (or if it fails). */
const FALLBACK: ApiFooter = {
  brandName: 'EREZER',
  blurb: 'A complete lifestyle brand — footwear, clothing, watches, and bags curated for everyday confidence across Bangladesh.',
  columns: [
    { title: 'Quick Info', links: [
      { label: 'About Us', url: '/about' },
      { label: 'Contact Us', url: '/contact' },
      { label: 'Track Your Order', url: '/orders' },
      { label: 'All Categories', url: '/shop' },
      { label: 'New & Popular', url: '/shop' },
    ] },
    { title: 'Useful Links', links: [
      { label: 'New Arrivals', url: '/shop' },
      { label: 'Collections', url: '/shop' },
      { label: 'My Account', url: '/account' },
      { label: 'My Orders', url: '/orders' },
      { label: 'Wishlist', url: '/wishlist' },
    ] },
  ],
  promises: [
    { icon: 'quality', title: 'Comfort & Quality Assured', description: 'Thoughtfully selected with quality finishing.' },
    { icon: 'support', title: 'In-Store & Online Support', description: 'Visit us or order easily — responsive service.' },
    { icon: 'delivery', title: 'Nationwide Delivery', description: 'Smooth and reliable delivery across Bangladesh.' },
    { icon: 'globe', title: 'International Orders', description: 'WhatsApp: +880 1700-000000' },
  ],
  outlets: [],
  copyright: '© 2026 EREZER STORE — Handcrafted for Confidence.',
  tagline: 'Secure payments • Nationwide shipping',
};

@Component({
  selector: 'app-footer',
  imports: [RouterLink],
  template: `
    <footer class="border-t border-neutral-200 bg-white dark:border-neutral-800 dark:bg-neutral-950">

      <!-- ── Our promise strip ──────────────────────────────────────────────── -->
      @if (promises().length > 0) {
        <div class="border-b border-neutral-200 dark:border-neutral-800">
          <div class="mx-auto grid w-full max-w-7xl gap-6 px-4 py-10 sm:px-6 lg:px-8 sm:grid-cols-2 lg:grid-cols-4">
            @for (p of promises(); track $index) {
              <div class="flex items-start gap-3">
                <span class="mt-0.5 inline-flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full bg-neutral-900 text-white dark:bg-white dark:text-black">
                  @switch (p.icon) {
                    @case ('quality') {
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12c0 5.25-3.6 8.25-8.4 9.6a1.2 1.2 0 01-.6 0C7.2 20.25 3.6 17.25 3.6 12V6.3a1.2 1.2 0 01.75-1.11l7.2-2.7a1.2 1.2 0 01.9 0l7.2 2.7A1.2 1.2 0 0121 6.3z"/></svg>
                    }
                    @case ('support') {
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M8.625 12a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0H8.25m4.125 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0H12m4.125 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0h-.375M21 12c0 4.556-4.03 8.25-9 8.25a9.764 9.764 0 01-2.555-.337A5.972 5.972 0 015.41 20.97a5.969 5.969 0 01-.474-.065 4.48 4.48 0 00.978-2.025c.09-.457-.133-.901-.467-1.226C3.93 16.178 3 14.189 3 12c0-4.556 4.03-8.25 9-8.25s9 3.694 9 8.25z"/></svg>
                    }
                    @case ('delivery') {
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M8.25 18.75a1.5 1.5 0 11-3 0m3 0a1.5 1.5 0 00-3 0m3 0h6m-9 0H3.375a1.125 1.125 0 01-1.125-1.125V14.25m17.25 4.5a1.5 1.5 0 11-3 0m3 0a1.5 1.5 0 00-3 0m3 0h1.125c.621 0 1.129-.504 1.09-1.124a17.902 17.902 0 00-3.213-9.193 2.056 2.056 0 00-1.66-.84H14.25M16.5 18.75h-6V5.25A1.125 1.125 0 009.375 4.125H4.5"/></svg>
                    }
                    @case ('star') {
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 24 24" fill="currentColor"><path d="M12 17.27l5.18 3.04-1.37-5.88 4.56-3.95-6.01-.51L12 4.5l-2.36 5.47-6.01.51 4.56 3.95-1.37 5.88z"/></svg>
                    }
                    @default {
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M12 21a9 9 0 100-18 9 9 0 000 18zm0 0a8.949 8.949 0 004.951-1.488A3.987 3.987 0 0013 16h-2a3.987 3.987 0 00-3.951 3.512A8.949 8.949 0 0012 21zm3-11a3 3 0 11-6 0 3 3 0 016 0z"/></svg>
                    }
                  }
                </span>
                <div>
                  <p class="text-sm font-semibold text-neutral-900 dark:text-neutral-100">{{ p.title }}</p>
                  <p class="mt-0.5 text-xs text-neutral-500 dark:text-neutral-400">{{ p.description }}</p>
                </div>
              </div>
            }
          </div>
        </div>
      }

      <!-- ── Brand + link columns ───────────────────────────────────────────── -->
      <div class="mx-auto grid w-full max-w-7xl gap-8 px-4 py-12 text-sm text-neutral-600 dark:text-neutral-400 sm:px-6 lg:px-8 md:grid-cols-4">
        <div class="space-y-2 md:col-span-2">
          <p class="text-base font-semibold tracking-[0.2em] text-neutral-900 dark:text-neutral-100">{{ footer().brandName }}</p>
          <p class="max-w-md">{{ footer().blurb }}</p>
        </div>
        @for (col of footer().columns; track col.title) {
          <div class="space-y-2">
            <p class="text-xs font-semibold uppercase tracking-[0.2em] text-neutral-500 dark:text-neutral-400">{{ col.title }}</p>
            @for (link of col.links; track link.label) {
              @if (!link.url) {
                <p>{{ link.label }}</p>
              } @else if (isInternal(link.url)) {
                <a [routerLink]="link.url" class="block transition hover:text-neutral-900 dark:hover:text-neutral-100">{{ link.label }}</a>
              } @else {
                <a [href]="link.url" class="block transition hover:text-neutral-900 dark:hover:text-neutral-100">{{ link.label }}</a>
              }
            }
          </div>
        }
      </div>

      <!-- ── Our outlets / store locations ──────────────────────────────────── -->
      @if (outlets().length > 0) {
        <div class="border-t border-neutral-200 dark:border-neutral-800">
          <div class="mx-auto w-full max-w-7xl px-4 py-12 sm:px-6 lg:px-8">
            <p class="text-xs font-semibold uppercase tracking-[0.2em] text-neutral-500 dark:text-neutral-400">Our Store Locations</p>
            <div class="mt-6 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
              @for (outlet of outlets(); track $index) {
                <div class="overflow-hidden rounded-2xl border border-neutral-200 bg-white dark:border-neutral-800 dark:bg-neutral-900">
                  @if (outlet.imageUrl) {
                    <div class="aspect-[16/10] overflow-hidden">
                      <img [src]="outlet.imageUrl" [alt]="outlet.name || 'Outlet'"
                        class="h-full w-full object-cover" loading="lazy" />
                    </div>
                  }
                  <div class="space-y-1 p-4">
                    <p class="text-sm font-semibold text-neutral-900 dark:text-neutral-100">{{ outlet.name }}</p>
                    @if (outlet.address) {
                      <p class="text-xs text-neutral-500 dark:text-neutral-400">{{ outlet.address }}</p>
                    }
                    @if (outlet.phone) {
                      <a [href]="'tel:' + outlet.phone"
                        class="inline-block text-xs font-medium text-neutral-700 transition hover:text-neutral-900 dark:text-neutral-300 dark:hover:text-neutral-100">
                        {{ outlet.phone }}
                      </a>
                    }
                  </div>
                </div>
              }
            </div>
          </div>
        </div>
      }

      <!-- ── Bottom bar ─────────────────────────────────────────────────────── -->
      <div class="border-t border-neutral-200 dark:border-neutral-800">
        <div class="mx-auto flex w-full max-w-7xl flex-wrap items-center justify-between gap-2 px-4 py-4 text-xs text-neutral-500 dark:text-neutral-400 sm:px-6 lg:px-8">
          <p>{{ footer().copyright }}</p>
          <p>{{ footer().tagline }}</p>
        </div>
      </div>
    </footer>
  `
})
export class FooterComponent {
  private readonly settings = inject(SettingsStore);

  protected readonly footer = computed<ApiFooter>(() => this.settings.settings()?.footer ?? FALLBACK);

  protected readonly promises = computed<ApiFooterPromise[]>(() => {
    const f = this.settings.settings()?.footer;
    if (!f) return FALLBACK.promises ?? [];
    return f.promises ?? [];
  });

  protected readonly outlets = computed<ApiFooterOutlet[]>(() => this.footer().outlets ?? []);

  protected isInternal(url: string): boolean {
    return !!url && url.startsWith('/');
  }
}
