import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { inject, Injectable, PLATFORM_ID } from '@angular/core';
import { META_PIXEL_ID } from './config';

type Fbq = (...args: unknown[]) => void;

/** One line of a cart or order, as Meta's `contents` parameter wants it. */
export interface PixelLine {
  id: string | number;
  name?: string;
  quantity: number;
  /** Unit price in BDT. */
  price: number;
}

/** What Meta may use to match a shopper to a Facebook account (advanced matching). */
export interface PixelUser {
  email?: string | null;
  phone?: string | null;
  firstName?: string | null;
  lastName?: string | null;
  /** Your own customer id; hashed by the pixel before sending. */
  externalId?: string | null;
}

/** Stored across the bKash redirect so Purchase can fire only once payment succeeds. */
interface PendingPurchase {
  orderId: string;
  value: number;
  contents: PixelLine[];
}

const PENDING_PURCHASE_KEY = 'erezer-pending-purchase';
const CURRENCY = 'BDT';

/**
 * Meta (Facebook) Pixel wrapper for the storefront.
 *
 * No-op unless {@link META_PIXEL_ID} is set and we are in a browser. Every
 * standard e-commerce event carries the product ids Meta's catalog ads and
 * per-product reporting need, and every event carries an `eventID` so the
 * backend's Conversions API copy of the same event is deduplicated rather
 * than double counted.
 */
@Injectable({ providedIn: 'root' })
export class PixelService {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly document = inject(DOCUMENT);
  private initialised = false;
  private user: PixelUser = {};

  /** Injects the pixel base code once. Safe to call repeatedly. */
  init(): void {
    if (this.initialised || !this.enabled()) return;
    this.initialised = true;

    const win = this.window();
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

    win.fbq('init', META_PIXEL_ID, this.matchingData());
    win.fbq('track', 'PageView', {}, { eventID: this.eventId('pageview') });
  }

  /**
   * Tell the pixel who the shopper is, so Meta can attribute their purchases
   * to the ad they clicked. Values are normalised here and hashed by the pixel
   * before they leave the browser; nothing is sent in clear text.
   */
  setUser(user: PixelUser): void {
    this.user = { ...this.user, ...user };
    if (!this.initialised) return;
    const fbq = this.fbq();
    if (!fbq) return;
    // Re-initialising with the same id updates the matching data in place.
    fbq('init', META_PIXEL_ID, this.matchingData());
  }

  // ── standard events ─────────────────────────────────────────────────────

  pageView(): void {
    this.track('PageView', {}, 'pageview');
  }

  viewContent(id: string | number, name: string, value: number): void {
    this.track('ViewContent', {
      content_ids: [String(id)], content_name: name, content_type: 'product',
      value, currency: CURRENCY,
    }, 'view-' + id);
  }

  addToCart(id: string | number, name: string, value: number, quantity: number): void {
    this.track('AddToCart', {
      content_ids: [String(id)], content_name: name, content_type: 'product',
      value: value * quantity, currency: CURRENCY,
      contents: [{ id: String(id), quantity, item_price: value }],
    }, 'cart-' + id);
  }

  addToWishlist(id: string | number, name: string, value: number): void {
    this.track('AddToWishlist', {
      content_ids: [String(id)], content_name: name, content_type: 'product',
      value, currency: CURRENCY,
    }, 'wish-' + id);
  }

  search(query: string): void {
    const q = query.trim();
    if (!q) return;
    this.track('Search', { search_string: q, content_type: 'product' }, 'search');
  }

  initiateCheckout(lines: PixelLine[], value: number): void {
    this.track('InitiateCheckout', {
      ...this.contentsData(lines),
      value, currency: CURRENCY,
    }, 'checkout');
  }

  addPaymentInfo(method: string, value: number): void {
    this.track('AddPaymentInfo', { payment_method: method, value, currency: CURRENCY }, 'payment');
  }

  /**
   * A confirmed sale. `orderId` doubles as the deduplication key, so the
   * backend must send its Conversions API copy with the same id.
   */
  purchase(orderId: string, value: number, lines: PixelLine[]): void {
    this.track('Purchase', {
      ...this.contentsData(lines),
      value, currency: CURRENCY, order_id: orderId,
    }, 'purchase-' + orderId, /* stableId */ true);
  }

  /**
   * bKash sends the shopper away to pay and back again. The cart is gone by
   * then, so the checkout page parks what it would have reported here, and the
   * return page fires it only if the payment actually completed.
   */
  deferPurchase(orderId: string, value: number, lines: PixelLine[]): void {
    try {
      const pending: PendingPurchase = { orderId, value, contents: lines };
      this.window()?.sessionStorage.setItem(PENDING_PURCHASE_KEY, JSON.stringify(pending));
    } catch { /* storage unavailable: the server-side event still reports it */ }
  }

  /** Fire the purchase parked by {@link deferPurchase}, if it matches this order. */
  purchaseDeferred(orderId: string, fallbackValue?: number): void {
    let pending: PendingPurchase | null = null;
    try {
      const raw = this.window()?.sessionStorage.getItem(PENDING_PURCHASE_KEY);
      if (raw) pending = JSON.parse(raw) as PendingPurchase;
      this.window()?.sessionStorage.removeItem(PENDING_PURCHASE_KEY);
    } catch { /* ignore */ }

    if (pending && pending.orderId === orderId) {
      this.purchase(orderId, pending.value, pending.contents);
    } else if (fallbackValue != null) {
      this.purchase(orderId, fallbackValue, []);
    }
  }

  completeRegistration(): void {
    this.track('CompleteRegistration', { status: 'completed' }, 'register');
  }

  /** A newsletter sign-up or similar expression of interest. */
  lead(source: string): void {
    this.track('Lead', { content_name: source }, 'lead');
  }

  contact(): void {
    this.track('Contact', {}, 'contact');
  }

  // ── cookies the server needs for its copy of the events ─────────────────

  /** Meta's browser id cookie, set by the pixel on this site. */
  fbp(): string | null { return this.cookie('_fbp'); }

  /** Meta's click id cookie, present when the shopper arrived from an ad. */
  fbc(): string | null { return this.cookie('_fbc'); }

  // ── internals ───────────────────────────────────────────────────────────

  private contentsData(lines: PixelLine[]) {
    return {
      content_type: 'product',
      content_ids: lines.map((l) => String(l.id)),
      contents: lines.map((l) => ({ id: String(l.id), quantity: l.quantity, item_price: l.price })),
      num_items: lines.reduce((n, l) => n + l.quantity, 0),
    };
  }

  private track(event: string, data: Record<string, unknown>, idPrefix: string, stableId = false): void {
    if (!this.enabled()) return;
    const fbq = this.fbq();
    if (!fbq) return;
    fbq('track', event, data, { eventID: stableId ? idPrefix : this.eventId(idPrefix) });
  }

  /** Random per-event id; Purchase uses the order id instead so both halves agree. */
  private eventId(prefix: string): string {
    const rnd = typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : Math.random().toString(36).slice(2) + Date.now().toString(36);
    return prefix + '-' + rnd;
  }

  /** Normalised the way Meta's matching expects, then hashed by the pixel itself. */
  private matchingData(): Record<string, string> {
    const out: Record<string, string> = {};
    // A returning shopper is known before any page asks: the session is in
    // localStorage, so the very first PageView can already be matched.
    const stored = (key: string): string | null => {
      try { return this.window()?.localStorage.getItem(key) ?? null; } catch { return null; }
    };
    this.user = {
      email: this.user.email ?? stored('erezer-email'),
      firstName: this.user.firstName ?? stored('erezer-firstName'),
      lastName: this.user.lastName ?? stored('erezer-lastName'),
      externalId: this.user.externalId ?? stored('erezer-userId'),
      phone: this.user.phone,
    };
    const email = this.user.email?.trim().toLowerCase();
    if (email) out['em'] = email;
    const phone = normalisePhone(this.user.phone);
    if (phone) out['ph'] = phone;
    const fn = this.user.firstName?.trim().toLowerCase();
    if (fn) out['fn'] = fn;
    const ln = this.user.lastName?.trim().toLowerCase();
    if (ln) out['ln'] = ln;
    if (this.user.externalId) out['external_id'] = String(this.user.externalId);
    out['country'] = 'bd';
    return out;
  }

  private cookie(name: string): string | null {
    const raw = this.document?.cookie ?? '';
    const match = raw.split('; ').find((c) => c.startsWith(name + '='));
    return match ? decodeURIComponent(match.slice(name.length + 1)) : null;
  }

  private window(): (Window & { fbq?: Fbq; _fbq?: Fbq }) | null {
    return this.document.defaultView as (Window & { fbq?: Fbq; _fbq?: Fbq }) | null;
  }

  private fbq(): Fbq | undefined {
    return this.window()?.fbq;
  }

  private enabled(): boolean {
    return isPlatformBrowser(this.platformId) && !!META_PIXEL_ID;
  }
}

/**
 * Digits only, with Bangladesh's country code. "01712-345678" becomes
 * "8801712345678", which is the form Meta matches on.
 */
export function normalisePhone(raw: string | null | undefined): string | null {
  if (!raw) return null;
  let digits = raw.replace(/\D/g, '');
  if (!digits) return null;
  if (digits.startsWith('880')) return digits;
  if (digits.startsWith('0')) digits = digits.slice(1);
  return '880' + digits;
}
