import { Injectable, NgZone, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

/** Remembered for the browser tab, so a reload while browsing keeps the way back. */
const KEY = 'erezer-showcase-visit';
/** With nobody touching the screen for this long, it goes back to the showcase. */
const IDLE_MS = 90_000;
/** The last seconds of that are counted down on the Back button. */
const WARN_MS = 10_000;
/** A screen at least this wide is the store's screen or a computer; narrower is a phone. */
const SHOP_SCREEN = () => typeof window !== 'undefined' && window.matchMedia('(min-width: 768px)').matches;

/**
 * A customer browsing the shop from the in-store showcase screen: they tapped a
 * slide and went into the shop. Every page then shows a Back to showcase button,
 * and when the screen is left alone it goes back by itself.
 */
@Injectable({ providedIn: 'root' })
export class ShowcaseVisitService {
  private readonly router = inject(Router);
  private readonly zone = inject(NgZone);

  /** True while someone browses from the showcase screen. */
  readonly active = signal(this.remembered());
  /** Seconds left before going back by itself, in the last few; null until then. */
  readonly secondsLeft = signal<number | null>(null);

  private lastTouch = Date.now();
  private ticker?: ReturnType<typeof setInterval>;

  constructor() {
    if (this.active()) this.watch();
  }

  /**
   * From the showcase into the shop. On a phone it is simply a link: the phone is the
   * customer's own, so no Back button (it would cover the Add to cart bar) and no
   * going back by itself.
   */
  visit(url: string): void {
    if (!SHOP_SCREEN()) {
      void this.router.navigateByUrl(url);
      return;
    }
    this.active.set(true);
    try { sessionStorage.setItem(KEY, '1'); } catch { /* private mode: the button still shows until reload */ }
    this.watch();
    void this.router.navigateByUrl(url);
  }

  /** Back to the showcase. The screen stays a showcase screen, so the next visit works the same way. */
  back(): void {
    this.stop();
    void this.router.navigateByUrl('/showcase');
  }

  /** On the showcase itself nothing needs watching. */
  stop(): void {
    this.active.set(false);
    this.secondsLeft.set(null);
    try { sessionStorage.removeItem(KEY); } catch { /* nothing stored */ }
    clearInterval(this.ticker);
    this.ticker = undefined;
    for (const e of ['pointerdown', 'keydown', 'wheel', 'touchstart', 'scroll']) window.removeEventListener(e, this.touched, true);
  }

  private watch(): void {
    this.lastTouch = Date.now();
    if (this.ticker) return;
    for (const e of ['pointerdown', 'keydown', 'wheel', 'touchstart', 'scroll']) window.addEventListener(e, this.touched, { capture: true, passive: true });
    this.zone.runOutsideAngular(() => {
      this.ticker = setInterval(() => {
        const left = IDLE_MS - (Date.now() - this.lastTouch);
        const warn = left <= WARN_MS ? Math.max(0, Math.ceil(left / 1000)) : null;
        if (warn !== this.secondsLeft()) this.zone.run(() => this.secondsLeft.set(warn));
        if (left <= 0) this.zone.run(() => this.back());
      }, 500);
    });
  }

  private touched = (): void => {
    this.lastTouch = Date.now();
  };

  private remembered(): boolean {
    try { return typeof sessionStorage !== 'undefined' && sessionStorage.getItem(KEY) === '1'; } catch { return false; }
  }
}
