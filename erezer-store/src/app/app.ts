import { Component, effect, inject } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { catchError, filter, of } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HeaderComponent } from './components/layout/header.component';
import { FooterComponent } from './components/layout/footer.component';
import { ThemeService } from './core/theme.service';
import { PixelService } from './core/pixel.service';
import { SettingsStore } from './core/store/settings.store';
import { AuthService } from './core/auth.service';
import { ApiService } from './core/api.service';
import { EcommerceStore } from './core/store/ecommerce.store';
import { SeoService } from './core/seo.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, HeaderComponent, FooterComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private readonly themeService = inject(ThemeService);
  private readonly pixel = inject(PixelService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly store = inject(EcommerceStore);
  private readonly settings = inject(SettingsStore);
  private readonly seo = inject(SeoService);

  constructor() {
    this.themeService.initializeTheme();
    this.seo.followRoutes();
    this.publishScrollbarWidth();

    // Meta Pixel: start with whatever the server image was built with, then take
    // the ID the owner set in the admin panel as soon as the settings arrive.
    this.pixel.init();
    effect(() => this.pixel.useId(this.settings.settings()?.metaPixelId));
    // Advanced matching: once the shopper signs in, let Meta match their
    // purchases to the ad they clicked. Hashed by the pixel before sending.
    effect(() => this.pixel.setUser({ email: this.auth.email(), externalId: this.auth.userId() }));
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      takeUntilDestroyed(),
    ).subscribe(() => this.pixel.pageView());

    this.restoreSession();
  }

  /**
   * Publishes the vertical scrollbar's width as --sbw.
   *
   * Full-bleed sections size themselves from 100vw, but 100vw *includes* the
   * scrollbar while the usable viewport does not - so a naive full-bleed
   * overflows by the scrollbar width and adds a horizontal scrollbar. Headless
   * browsers use overlay scrollbars and hide the problem, which is exactly how
   * it slips through. Measuring it makes the maths exact on every platform.
   *
   * It is the width the scrollbar is taking *right now* that matters, and that
   * changes without the window changing size: the page grows long enough to
   * scroll, or the kind of scrollbar changes (Chrome's device toolbar switching
   * between a phone and a desktop of the same width). A figure measured once
   * goes stale and leaves every full-bleed section off-centre, with the page a
   * few pixels wider than the screen. So it is read from what the browser is
   * actually doing, and read again whenever the page's usable width changes.
   */
  private publishScrollbarWidth(): void {
    if (typeof window === 'undefined') return;
    const root = document.documentElement;
    let published = '';
    const apply = () => {
      // What the scrollbar occupies at this moment: the window's width less the
      // width left for the page. Zero when there is no scrollbar, or it floats
      // over the page as on phones — which is exactly what the sums need.
      const width = `${Math.max(0, window.innerWidth - root.clientWidth)}px`;
      if (width === published) return;
      published = width;
      root.style.setProperty('--sbw', width);
    };
    apply();
    window.addEventListener('resize', apply, { passive: true });
    // The page's usable width changes exactly when a scrollbar appears, goes
    // away or changes kind — none of which is a window resize.
    if (typeof ResizeObserver !== 'undefined') {
      new ResizeObserver(apply).observe(root);
    }
  }

  /**
   * On every app load, if a session exists:
   *  - reload the authoritative cart from the server so items survive a refresh
   *    (e.g. after the customer leaves to verify their email), and
   *  - refresh the token so {@code emailVerified} reflects server truth — this
   *    clears the "verify your email" warning once they've confirmed, even if
   *    they verified on another device.
   */
  private restoreSession(): void {
    const userId = this.auth.userId();
    if (!this.auth.isAuthenticated() || !userId) return;

    this.api.getCart(userId).pipe(catchError(() => of([]))).subscribe((items) => {
      if (items.length > 0) this.store.loadApiCart(items);
    });

    // Pull a fresh token (and thus fresh emailVerified). Ignore failures so a
    // stale refresh token never hard-logs-out a browsing customer.
    this.auth.refresh().catch(() => { /* keep the existing session */ });
  }
}
