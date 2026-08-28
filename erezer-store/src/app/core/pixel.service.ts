import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { inject, Injectable, PLATFORM_ID } from '@angular/core';
import { META_PIXEL_ID } from './config';

type Fbq = (...args: unknown[]) => void;

/**
 * Thin Meta (Facebook) Pixel wrapper. No-op unless {@link META_PIXEL_ID} is set
 * and we're running in a browser (SSR-safe). Inject and call the track helpers
 * from page components.
 */
@Injectable({ providedIn: 'root' })
export class PixelService {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly document = inject(DOCUMENT);
  private initialised = false;

  /** Injects the pixel base code once. Safe to call repeatedly. */
  init(): void {
    if (this.initialised || !this.enabled()) return;
    this.initialised = true;

    const win = this.document.defaultView as (Window & { fbq?: Fbq; _fbq?: Fbq }) | null;
    if (!win) return;

    /* Standard Meta Pixel bootstrap (adapted to inject into DOCUMENT). */
    const n: Fbq & { callMethod?: Fbq; queue?: unknown[]; push?: unknown; loaded?: boolean; version?: string } =
      function (...args: unknown[]) {
        if (n.callMethod) { n.callMethod(...args); } else { n.queue!.push(args); }
      } as Fbq;
    if (!win.fbq) win.fbq = n;
    win._fbq = win._fbq || n;
    n.push = n;
    n.loaded = true;
    n.version = '2.0';
    n.queue = [];

    const script = this.document.createElement('script');
    script.async = true;
    script.src = 'https://connect.facebook.net/en_US/fbevents.js';
    this.document.head.appendChild(script);

    win.fbq('init', META_PIXEL_ID);
    win.fbq('track', 'PageView');
  }

  pageView(): void {
    this.track('PageView');
  }

  viewContent(id: string | number, name: string, value: number): void {
    this.track('ViewContent', {
      content_ids: [String(id)], content_name: name, content_type: 'product',
      value, currency: 'BDT',
    });
  }

  addToCart(id: string | number, name: string, value: number, quantity: number): void {
    this.track('AddToCart', {
      content_ids: [String(id)], content_name: name, content_type: 'product',
      value, currency: 'BDT', contents: [{ id: String(id), quantity }],
    });
  }

  purchase(orderId: string, value: number): void {
    this.track('Purchase', { value, currency: 'BDT', content_type: 'product', order_id: orderId });
  }

  private track(event: string, data?: Record<string, unknown>): void {
    if (!this.enabled()) return;
    const fbq = (this.document.defaultView as (Window & { fbq?: Fbq }) | null)?.fbq;
    if (!fbq) return;
    if (data) { fbq('track', event, data); } else { fbq('track', event); }
  }

  private enabled(): boolean {
    return isPlatformBrowser(this.platformId) && !!META_PIXEL_ID;
  }
}
