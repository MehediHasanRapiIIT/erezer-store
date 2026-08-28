import { Component, inject } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { catchError, filter, of } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HeaderComponent } from './components/layout/header.component';
import { FooterComponent } from './components/layout/footer.component';
import { ThemeService } from './core/theme.service';
import { PixelService } from './core/pixel.service';
import { AuthService } from './core/auth.service';
import { ApiService } from './core/api.service';
import { EcommerceStore } from './core/store/ecommerce.store';

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

  constructor() {
    this.themeService.initializeTheme();

    // Meta Pixel: inject base code once, then fire a PageView per navigation.
    this.pixel.init();
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      takeUntilDestroyed(),
    ).subscribe(() => this.pixel.pageView());

    this.restoreSession();
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
