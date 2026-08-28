import { AfterViewInit, Component, computed, effect, ElementRef, inject, OnDestroy, signal, HostListener } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EcommerceStore } from '../../core/store/ecommerce.store';
import { AuthService } from '../../core/auth.service';
import { TranslateService } from '../../core/i18n/translate.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { Lang } from '../../core/i18n/dictionaries';
import { ThemeToggleComponent } from '../shared/theme-toggle.component';

interface NavLink {
  route: string;
  key: string;
  authOnly: boolean;
}

/** Scroll distance before the floating header solidifies. */
const HERO_FADE_PX = 24;

@Component({
  selector: 'app-header',
  imports: [RouterLink, RouterLinkActive, TranslatePipe, ThemeToggleComponent],
  template: `
    <header
      class="sticky top-0 z-40 transition-colors duration-300"
      [class]="overHero()
        ? 'header-over-hero sticky top-0 z-40 border-b border-transparent bg-transparent transition-colors duration-300'
        : 'sticky top-0 z-40 border-b border-neutral-200/80 bg-white/85 backdrop-blur-xl transition-colors duration-300 dark:border-neutral-800 dark:bg-neutral-950/85'"
    >
      <div class="mx-auto flex w-full max-w-7xl items-center gap-3 px-4 py-3 sm:gap-4 sm:px-6 lg:px-8">
        <!-- Mobile menu trigger -->
        <button
          type="button"
          (click)="openMenu()"
          class="-ml-1 inline-flex h-10 w-10 items-center justify-center rounded-full text-neutral-700 transition hover:bg-neutral-100 dark:text-neutral-200 dark:hover:bg-neutral-800 md:hidden"
          aria-label="Open menu"
        >
          <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
            <path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5M3.75 17.25h16.5" />
          </svg>
        </button>

        <a routerLink="/" class="shrink-0 text-lg font-semibold tracking-[0.22em] sm:text-xl">EREZER</a>

        <!-- Desktop nav -->
        <nav class="ml-2 hidden items-center gap-1 text-sm md:flex">
          @for (link of visibleLinks(); track link.route) {
            <a
              [routerLink]="link.route"
              routerLinkActive="bg-neutral-100 text-black dark:bg-neutral-800 dark:!text-white"
              class="rounded-full px-3 py-1.5 text-neutral-600 transition hover:text-black dark:text-neutral-300 dark:hover:text-white"
            >{{ link.key | t }}</a>
          }
          <a
            routerLink="/admin"
            routerLinkActive="bg-neutral-100 text-black dark:bg-neutral-800 dark:!text-white"
            class="rounded-full px-3 py-1.5 text-neutral-600 transition hover:text-black dark:text-neutral-300 dark:hover:text-white"
          >Admin</a>
        </nav>

        <div class="ml-auto flex items-center gap-1 sm:gap-2">
          <!-- Language switcher (sm+) -->
          <div class="hidden items-center gap-1 sm:inline-flex">
            @for (l of translate.supported; track l.id) {
              <button
                type="button"
                (click)="setLang(l.id)"
                class="rounded-full border px-2.5 py-1 text-xs font-medium transition"
                [class.border-black]="translate.lang() === l.id"
                [class.dark:border-white]="translate.lang() === l.id"
                [class.bg-black]="translate.lang() === l.id"
                [class.text-white]="translate.lang() === l.id"
                [class.dark:bg-white]="translate.lang() === l.id"
                [class.dark:text-black]="translate.lang() === l.id"
                [class.border-neutral-300]="translate.lang() !== l.id"
                [class.dark:border-neutral-700]="translate.lang() !== l.id"
              >{{ l.native }}</button>
            }
          </div>

          <!-- Theme toggle (sm+) -->
          <app-theme-toggle class="hidden sm:inline-flex" />

          <!-- Wishlist (icon + badge) -->
          <a
            routerLink="/wishlist"
            class="relative inline-flex h-10 w-10 items-center justify-center rounded-full text-neutral-700 transition hover:bg-neutral-100 dark:text-neutral-200 dark:hover:bg-neutral-800"
            routerLinkActive="bg-neutral-100 dark:bg-neutral-800"
            aria-label="Wishlist"
          >
            <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 7.22 9 12 9 12s9-4.78 9-12z" /></svg>
            @if (wishCount() > 0) {
              <span class="absolute -right-0.5 -top-0.5 inline-flex min-w-[18px] items-center justify-center rounded-full bg-black px-1 py-0.5 text-[10px] font-semibold leading-none text-white dark:bg-white dark:text-black">{{ wishCount() }}</span>
            }
          </a>

          <!-- Cart (icon + badge) -->
          <a
            routerLink="/cart"
            class="relative inline-flex h-10 w-10 items-center justify-center rounded-full text-neutral-700 transition hover:bg-neutral-100 dark:text-neutral-200 dark:hover:bg-neutral-800"
            routerLinkActive="bg-neutral-100 dark:bg-neutral-800"
            [attr.aria-label]="('header.cart' | t)"
          >
            <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M2.25 3h1.386c.51 0 .955.343 1.087.835l.383 1.437M7.5 14.25a3 3 0 00-3 3h15.75m-12.75-3h11.218c1.121-2.3 2.1-4.684 2.924-7.138a60.114 60.114 0 00-16.536-1.84M7.5 14.25L5.106 5.272M6 20.25a.75.75 0 11-1.5 0 .75.75 0 011.5 0zm12.75 0a.75.75 0 11-1.5 0 .75.75 0 011.5 0z" /></svg>
            @if (store.cartCount() > 0) {
              <span class="absolute -right-0.5 -top-0.5 inline-flex min-w-[18px] items-center justify-center rounded-full bg-black px-1 py-0.5 text-[10px] font-semibold leading-none text-white dark:bg-white dark:text-black">{{ store.cartCount() }}</span>
            }
          </a>

          <!-- Auth (sm+) -->
          @if (auth.isAuthenticated()) {
            <button
              type="button"
              (click)="logout()"
              class="ml-1 hidden rounded-full border border-neutral-300 px-3.5 py-1.5 text-xs font-medium text-neutral-700 transition hover:border-neutral-400 hover:bg-neutral-100 dark:border-neutral-700 dark:text-neutral-200 dark:hover:bg-neutral-800 sm:inline-flex"
            >Log out</button>
          } @else {
            <a
              routerLink="/account"
              class="ml-1 hidden rounded-full bg-black px-4 py-1.5 text-xs font-semibold text-white transition hover:opacity-90 dark:bg-white dark:text-black sm:inline-flex"
            >Sign in</a>
          }
        </div>
      </div>
    </header>

    <!-- ── Mobile drawer (unmounted when closed) ──────────────────────────── -->
    @if (menuOpen()) {
    <!-- Backdrop -->
    <div
      class="animate-overlay-in fixed inset-0 z-40 bg-black/40 backdrop-blur-sm md:hidden"
      (click)="closeMenu()"
      aria-hidden="true"
    ></div>

    <!-- Panel -->
    <aside
      class="animate-drawer-in fixed right-0 top-0 z-50 flex h-full w-[82%] max-w-sm flex-col bg-white shadow-2xl dark:bg-neutral-950 md:hidden"
      role="dialog"
      aria-modal="true"
    >
      <div class="flex items-center justify-between border-b border-neutral-200 px-5 py-4 dark:border-neutral-800">
        <span class="text-lg font-semibold tracking-[0.22em]">EREZER</span>
        <button
          type="button"
          (click)="closeMenu()"
          class="inline-flex h-10 w-10 items-center justify-center rounded-full text-neutral-700 transition hover:bg-neutral-100 dark:text-neutral-200 dark:hover:bg-neutral-800"
          aria-label="Close menu"
        >
          <svg class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" /></svg>
        </button>
      </div>

      <nav class="flex flex-1 flex-col gap-1 overflow-y-auto px-3 py-4 text-base">
        @for (link of visibleLinks(); track link.route) {
          <a
            [routerLink]="link.route"
            (click)="closeMenu()"
            routerLinkActive="bg-neutral-100 font-medium text-black dark:bg-neutral-800 dark:!text-white"
            class="rounded-xl px-4 py-3 text-neutral-700 transition hover:bg-neutral-100 dark:text-neutral-200 dark:hover:bg-neutral-800"
          >{{ link.key | t }}</a>
        }
        <a
          routerLink="/admin"
          (click)="closeMenu()"
          routerLinkActive="bg-neutral-100 font-medium text-black dark:bg-neutral-800 dark:!text-white"
          class="rounded-xl px-4 py-3 text-neutral-700 transition hover:bg-neutral-100 dark:text-neutral-200 dark:hover:bg-neutral-800"
        >Admin</a>
      </nav>

      <div class="space-y-4 border-t border-neutral-200 px-5 py-4 dark:border-neutral-800">
        <!-- Auth -->
        @if (auth.isAuthenticated()) {
          <button type="button" (click)="logout()" class="btn-secondary w-full">Log out</button>
        } @else {
          <a routerLink="/account" (click)="closeMenu()" class="btn-primary w-full">Sign in</a>
        }

        <!-- Language + theme -->
        <div class="flex items-center justify-between gap-3">
          <div class="inline-flex items-center gap-1">
            @for (l of translate.supported; track l.id) {
              <button
                type="button"
                (click)="setLang(l.id)"
                class="rounded-full border px-3 py-1.5 text-xs font-medium transition"
                [class.border-black]="translate.lang() === l.id"
                [class.dark:border-white]="translate.lang() === l.id"
                [class.bg-black]="translate.lang() === l.id"
                [class.text-white]="translate.lang() === l.id"
                [class.dark:bg-white]="translate.lang() === l.id"
                [class.dark:text-black]="translate.lang() === l.id"
                [class.border-neutral-300]="translate.lang() !== l.id"
                [class.dark:border-neutral-700]="translate.lang() !== l.id"
              >{{ l.native }}</button>
            }
          </div>
          <app-theme-toggle />
        </div>
      </div>
    </aside>
    }
  `,
  styles: [`
    /*
     * Floating header, used only over the home page's hero photo.
     *
     * The hero carries its own dark scrim, so everything in the bar is forced
     * to white for contrast. Scoped to this one state - the header keeps its
     * normal light/dark theming everywhere else. Elements that already carry
     * their own solid background (the Sign in pill, the active language chip)
     * are left alone, which is why this targets colour and border rather than
     * blanket-styling every child.
     */
    .header-over-hero,
    .header-over-hero a,
    .header-over-hero button {
      color: #fff;
    }
    .header-over-hero a:hover,
    .header-over-hero button:hover {
      color: #fff;
    }
    /* Outlined controls need a visible edge against photography. */
    .header-over-hero .ring-1,
    .header-over-hero [class*="border-neutral"] {
      border-color: rgba(255, 255, 255, 0.45);
    }
    /* Hover chips would flash near-white on a bright photo. */
    .header-over-hero *:hover {
      --tw-bg-opacity: 0.16;
    }
  `],
})
export class HeaderComponent implements AfterViewInit, OnDestroy {
  protected readonly store = inject(EcommerceStore);
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly translate = inject(TranslateService);
  private readonly document = inject(DOCUMENT);

  private readonly hostEl = inject(ElementRef);
  private resizeObserver?: ResizeObserver;

  protected readonly menuOpen = signal(false);

  /**
   * True while the bar floats over the home page's hero photo: on "/" and
   * scrolled to the top. Anywhere else, or once the user scrolls, the header
   * returns to its solid themed state so text stays legible against content.
   */
  private readonly atTop = signal(true);
  private readonly onHome = signal(true);
  protected readonly overHero = computed(() => this.onHome() && this.atTop());

  /**
   * Publishes the bar's height as --app-header-h.
   *
   * The home hero slides underneath the floating header, and it needs the exact
   * height to do that. Measuring instead of hardcoding keeps it correct when the
   * nav wraps to two lines on a narrow window or the links change.
   */
  private publishHeight(): void {
    if (typeof window === 'undefined') return;
    const el = this.hostEl.nativeElement as HTMLElement;
    const apply = () => this.document.documentElement.style
      .setProperty('--app-header-h', `${Math.round(el.getBoundingClientRect().height)}px`);
    apply();
    if (typeof ResizeObserver !== 'undefined') {
      this.resizeObserver = new ResizeObserver(apply);
      this.resizeObserver.observe(el);
    }
  }

  ngAfterViewInit(): void {
    this.publishHeight();
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
  }

  private isHomeUrl(url: string): boolean {
    return url.split('?')[0].split('#')[0] === '/';
  }

  private readScrollY(): number {
    return typeof window === 'undefined' ? 0 : window.scrollY;
  }

  // Passive by default in Angular's host listeners; cheap enough at this rate.
  @HostListener('window:scroll')
  protected onWindowScroll(): void {
    this.atTop.set(this.readScrollY() <= HERO_FADE_PX);
  }

  protected readonly wishCount = computed(() => this.store.wishlist().length);

  private readonly navLinks: NavLink[] = [
    { route: '/shop', key: 'header.shop', authOnly: false },
    { route: '/custom-design', key: 'header.custom_design', authOnly: false },
    { route: '/bundles', key: 'header.bundles', authOnly: false },
    { route: '/flash-sale', key: 'header.flash_sale', authOnly: false },
    { route: '/wishlist', key: 'header.wishlist', authOnly: false },
    { route: '/orders', key: 'header.orders', authOnly: true },
    { route: '/account', key: 'header.account', authOnly: true },
    { route: '/contact', key: 'header.contact', authOnly: false },
  ];

  protected readonly visibleLinks = computed(() =>
    this.navLinks.filter((l) => !l.authOnly || this.auth.isAuthenticated())
  );

  constructor() {
    // The floating-header state depends on the route, so seed it before any
    // navigation happens - otherwise a deep link renders white-on-white.
    this.onHome.set(this.isHomeUrl(this.router.url));

    // Close the drawer whenever navigation completes, and re-evaluate whether
    // the header should be floating over a hero on the newly-entered route.
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed()
      )
      .subscribe((e) => {
        this.menuOpen.set(false);
        this.onHome.set(this.isHomeUrl(e.urlAfterRedirects));
        this.atTop.set(this.readScrollY() <= HERO_FADE_PX);
      });

    // Lock body scroll while the mobile drawer is open.
    effect(() => {
      const body = this.document.body;
      if (!body) return;
      body.style.overflow = this.menuOpen() ? 'hidden' : '';
    });
  }

  protected openMenu(): void {
    this.menuOpen.set(true);
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  protected setLang(lang: Lang): void {
    this.translate.setLang(lang);
  }

  protected logout(): void {
    this.auth.signOut();
    this.store.cart.set([]);
    this.menuOpen.set(false);
    void this.router.navigateByUrl('/');
  }
}
