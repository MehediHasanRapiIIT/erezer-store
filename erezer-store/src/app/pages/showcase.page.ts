import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { catchError, forkJoin, of } from 'rxjs';
import QRCode from 'qrcode';
import { ApiService } from '../core/api.service';
import { ShowcaseVisitService } from '../core/showcase-visit.service';
import { ApiBundleOffer, ApiFlashSale, ApiProduct, ApiShowcaseSettings } from '../core/api.models';
import { EcommerceStore } from '../core/store/ecommerce.store';

/** One screenful of the showcase. */
type Slide =
  | { kind: 'brand'; key: string }
  | { kind: 'product'; key: string; product: ApiProduct; isNew: boolean }
  | { kind: 'flash'; key: string; sale: ApiFlashSale }
  | { kind: 'bundle'; key: string; bundle: ApiBundleOffer };

const DEFAULTS: ApiShowcaseSettings = { enabled: true, slideSeconds: 6, productMode: 'NEW', productIds: [], showQr: true };
/** How often the screen asks the shop again for products, offers and settings. */
const REFRESH_MS = 3 * 60 * 1000;
/** The old slide stays this long while the new one sweeps in over it. */
const HANDOVER_MS = 1100;
/** A product after every this many products is followed by an offer, when there are offers. */
const PRODUCTS_PER_OFFER = 4;

/**
 * The in-store showcase screen (/showcase): plays the shop's products, bundle offers
 * and flash sales by itself, like an advert, on the upright screen in the store. It fits
 * any screen: picture above the words when the screen is upright (the store's screen, a
 * phone), picture beside them when it is wide (a desktop, a TV). Settings are in admin,
 * Settings → Showcase screen. Nothing on it is clickable except the full-screen button.
 */
@Component({
  selector: 'app-showcase',
  imports: [CurrencyPipe],
  template: `
    <div class="stage" [class.calm]="!settings().enabled" (mousemove)="wake()" (touchstart)="wake()" data-testid="showcase">
      <div class="glow glow-a"></div>
      <div class="glow glow-b"></div>

      @for (s of shown(); track s.slide.key + s.at) {
        <section class="slide" [class.leaving]="s.leaving" [attr.data-kind]="s.slide.kind" data-testid="showcase-slide"
          (click)="open(s.slide)" role="link" [attr.aria-label]="'Open ' + label(s.slide)">
          @switch (s.slide.kind) {
            @case ('brand') {
              <div class="brand">
                <!-- The logo's shape only, painted by the shimmer behind it (as in the header). -->
                <span class="wordmark" role="img" aria-label="Erezer"
                  style="-webkit-mask:url(logo-wordmark.png) center/contain no-repeat;mask:url(logo-wordmark.png) center/contain no-repeat"></span>
                <p class="tagline">Erase the Ordinary</p>
                <p class="site">{{ site }}</p>
              </div>
            }
            @case ('product') {
              @if (asProduct(s.slide); as p) {
                <div class="backdrop" [style.background-image]="'url(' + p.product.imageUrl + ')'"></div>
                <div class="layout">
                  <div class="picture">
                    <img [src]="p.product.imageUrl" [alt]="p.product.name" />
                  </div>
                  <div class="words">
                    <p class="eyebrow">{{ p.isNew ? 'New arrival' : (p.product.categoryName || 'Erezer') }}</p>
                    <h2 class="name">{{ p.product.name }}</h2>
                    @if (priceOf(p.product); as price) {
                      <div class="prices">
                        <span class="price">{{ price.now | currency:'BDT':'৳':'1.0-0' }}</span>
                        @if (price.was) {
                          <span class="was">{{ price.was | currency:'BDT':'৳':'1.0-0' }}</span>
                          <span class="badge">-{{ price.off }}%</span>
                        }
                      </div>
                    }
                    @if (settings().showQr && qr(link('product/' + p.product.id)); as code) {
                      <div class="qr"><img [src]="code" alt="" /><span>Scan to shop<br />this piece</span></div>
                    }
                  </div>
                </div>
              }
            }
            @case ('flash') {
              @if (asFlash(s.slide); as f) {
                <div class="offer flash">
                  <div class="info">
                  <p class="eyebrow">{{ f.sale.label || 'Limited time' }}</p>
                  <h2 class="headline">Flash Sale</h2>
                  <p class="deal">{{ f.sale.name }} · {{ dealText(f.sale) }}</p>
                  <div class="clock" data-testid="showcase-clock">
                    @for (part of countdown(f.sale.endsAt); track $index) {
                      <div><b>{{ part.value }}</b><small>{{ part.unit }}</small></div>
                    }
                  </div>
                  </div>
                  <div class="tiles">
                    @for (p of f.sale.products.slice(0, 4); track p.id; let i = $index) {
                      <div class="tile" [style.animation-delay]="0.5 + i * 0.15 + 's'">
                        <img [src]="p.imageUrl" [alt]="p.name" />
                        <span class="tile-price">{{ flashPrice(f.sale, p) | currency:'BDT':'৳':'1.0-0' }}</span>
                      </div>
                    }
                  </div>
                  <div class="info">
                    @if (settings().showQr && qr(link('flash-sale/' + f.sale.id)); as code) {
                      <div class="qr"><img [src]="code" alt="" /><span>Scan for<br />the deals</span></div>
                    }
                  </div>
                </div>
              }
            }
            @case ('bundle') {
              @if (asBundle(s.slide); as b) {
                <div class="offer bundle">
                  <div class="info">
                  <p class="eyebrow">{{ b.bundle.label || 'Bundle offer' }}</p>
                  <h2 class="headline">{{ b.bundle.name }}</h2>
                  <p class="deal">{{ b.bundle.headline || b.bundle.description }}</p>
                  </div>
                  <div class="tiles">
                    @for (img of bundlePictures(b.bundle); track $index; let i = $index) {
                      <div class="tile" [style.animation-delay]="0.5 + i * 0.15 + 's'"><img [src]="img" alt="" /></div>
                    }
                  </div>
                  <div class="info">
                  @if (b.bundle.bundlePrice > 0) {
                    <div class="prices">
                      <span class="price">{{ b.bundle.bundlePrice | currency:'BDT':'৳':'1.0-0' }}</span>
                      @if (b.bundle.compareAtPrice && b.bundle.compareAtPrice > b.bundle.bundlePrice) {
                        <span class="was">{{ b.bundle.compareAtPrice | currency:'BDT':'৳':'1.0-0' }}</span>
                      }
                      @if (b.bundle.savings) {
                        <span class="badge">Save {{ b.bundle.savings | currency:'BDT':'৳':'1.0-0' }}</span>
                      }
                    </div>
                  }
                  @if (settings().showQr && qr(link('bundles/' + b.bundle.id)); as code) {
                    <div class="qr"><img [src]="code" alt="" /><span>Scan to<br />build yours</span></div>
                  }
                  </div>
                </div>
              }
            }
          }
        </section>
      }

      <p class="tap" data-testid="showcase-tap">Tap to {{ tapText() }}</p>

      @if (settings().enabled && slides().length > 1) {
        @for (n of [cycle()]; track n) {
          <div class="progress" [style.animation-duration]="currentSeconds() + 's'"></div>
        }
      }
      @if (awake() && canFullscreen) {
        <button type="button" class="fullscreen" (click)="fullscreen(); $event.stopPropagation()" data-testid="showcase-fullscreen">Full screen</button>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .stage {
      --ink: #fff; --dim: rgba(255,255,255,.62); --accent: #e11d48;
      position: fixed; inset: 0; overflow: hidden; background: #070707; color: var(--ink);
      font-family: inherit; cursor: pointer; user-select: none;
      /* Sizes follow the screen's smaller side, so a phone and a 1080x1920 screen look alike. */
      font-size: clamp(14px, 2.2vmin, 40px);
    }
    .glow { position: absolute; width: 70vmax; height: 70vmax; border-radius: 50%; filter: blur(80px); opacity: .35; pointer-events: none; }
    .glow-a { background: #7c3aed; top: -30vmax; left: -20vmax; animation: drift-a 22s ease-in-out infinite alternate; }
    .glow-b { background: #e11d48; bottom: -35vmax; right: -25vmax; animation: drift-b 26s ease-in-out infinite alternate; }
    @keyframes drift-a { to { transform: translate(25vmax, 30vmax) scale(1.2); } }
    @keyframes drift-b { to { transform: translate(-30vmax, -20vmax) scale(.9); } }

    .slide { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
      animation: sweep-in 1.1s cubic-bezier(.2,.8,.2,1) both; }
    .slide.leaving { animation: sweep-out 1.1s cubic-bezier(.6,0,.4,1) both; z-index: 0; }
    .slide:not(.leaving) { z-index: 1; }
    @keyframes sweep-in { from { clip-path: inset(0 0 0 100%); transform: translateX(6%); } to { clip-path: inset(0 0 0 0); transform: none; } }
    @keyframes sweep-out { to { transform: translateX(-12%) scale(.94); opacity: 0; } }

    /* Brand */
    .brand { text-align: center; padding: 6vmin; }
    .wordmark { display: block; width: min(80vw, 70vmin); aspect-ratio: 865 / 96; margin: 0 auto;
      background: linear-gradient(100deg, #fff 40%, #f9a8d4 50%, #fff 60%) 0 0 / 300% 100%;
      animation: rise 1.2s .3s both, shine 3.2s 1.2s ease-in-out infinite; }
    @keyframes shine { from { background-position: 100% 0; } to { background-position: -100% 0; } }
    .tagline { margin-top: 1.6em; font-size: 2.2em; font-weight: 300; letter-spacing: .3em; text-transform: uppercase; animation: rise 1s .9s both; }
    .site { margin-top: 1.2em; color: var(--dim); letter-spacing: .25em; animation: rise 1s 1.3s both; }

    /* Product */
    .backdrop { position: absolute; inset: -10%; background: center/cover no-repeat; filter: blur(40px) brightness(.35) saturate(1.3); }
    .layout { position: relative; width: 100%; height: 100%; display: grid; padding: 5vmin; gap: 4vmin;
      grid-template-rows: 1fr auto; }
    .picture { position: relative; min-height: 0; display: flex; align-items: center; justify-content: center; }
    .picture img { max-width: 100%; max-height: 100%; object-fit: contain; border-radius: 2.5vmin;
      box-shadow: 0 4vmin 10vmin rgba(0,0,0,.55); animation: pop .9s .2s both, kenburns 9s 1s ease-in-out both; }
    @keyframes pop { from { transform: scale(.85) translateY(4%); opacity: 0; } to { transform: none; opacity: 1; } }
    @keyframes kenburns { to { transform: scale(1.06); } }
    .words { position: relative; display: grid; grid-template-columns: 1fr auto; grid-template-areas: "eyebrow qr" "name qr" "prices qr";
      align-items: end; column-gap: 4vmin; }
    .eyebrow { grid-area: eyebrow; color: #f9a8d4; font-size: .9em; font-weight: 700; letter-spacing: .35em; text-transform: uppercase; animation: rise .8s .5s both; }
    .name { grid-area: name; margin: .25em 0; font-size: 2.6em; font-weight: 800; line-height: 1.05; animation: rise .8s .65s both; }
    .prices { grid-area: prices; display: flex; flex-wrap: wrap; align-items: baseline; gap: .5em .8em; }
    .price { font-size: 2.4em; font-weight: 800; animation: pop-price .7s .9s cubic-bezier(.3,1.6,.5,1) both; }
    .was { color: var(--dim); font-size: 1.3em; text-decoration: line-through; animation: rise .6s 1s both; }
    .badge { background: var(--accent); padding: .25em .7em; border-radius: 999px; font-weight: 800; font-size: 1.1em;
      animation: pop-price .6s 1.15s cubic-bezier(.3,1.8,.5,1) both, pulse 1.6s 2s ease-in-out infinite; }
    @keyframes pop-price { from { transform: scale(.3); opacity: 0; } to { transform: none; opacity: 1; } }
    @keyframes pulse { 50% { transform: scale(1.08); } }
    @keyframes rise { from { transform: translateY(40%); opacity: 0; } to { transform: none; opacity: 1; } }
    .qr { grid-area: qr; display: flex; flex-direction: column; align-items: center; gap: .5em; color: var(--dim);
      font-size: .8em; text-align: center; animation: rise .8s 1.3s both; }
    .qr img { width: clamp(84px, 16vmin, 260px); background: #fff; padding: .4em; border-radius: 1vmin; }

    /* Flash sale and bundles */
    .offer { position: relative; width: 100%; height: 100%; padding: 6vmin; display: flex; flex-direction: column;
      align-items: center; justify-content: center; text-align: center; gap: 2.5vmin; }
    .offer .eyebrow, .offer .prices { animation-delay: .3s; }
    /* Upright: everything in one column, words above and below the pictures. */
    .info { display: contents; }
    .headline { font-size: 4.2em; font-weight: 900; line-height: 1; letter-spacing: -.02em; animation: slam .8s .2s cubic-bezier(.2,1.4,.4,1) both; }
    .flash .headline { background: linear-gradient(90deg, #fde047, #fb7185, #fde047) 0 0 / 200% 100%; -webkit-background-clip: text;
      background-clip: text; color: transparent; animation: slam .8s .2s cubic-bezier(.2,1.4,.4,1) both, shine 2.5s 1s linear infinite; }
    @keyframes slam { from { transform: scale(2.2); opacity: 0; letter-spacing: .3em; } to { transform: none; opacity: 1; } }
    .deal { color: var(--dim); font-size: 1.4em; animation: rise .8s .45s both; }
    .clock { display: flex; gap: 1.2vmin; animation: rise .8s .6s both; }
    .clock div { background: rgba(255,255,255,.1); border: 1px solid rgba(255,255,255,.18); border-radius: 1.5vmin;
      padding: .5em .7em; min-width: 3.4em; }
    .clock b { display: block; font-size: 2.2em; font-variant-numeric: tabular-nums; }
    .clock small { color: var(--dim); font-size: .7em; letter-spacing: .2em; text-transform: uppercase; }
    .tiles { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 2vmin; width: min(100%, 80vmin); }
    .tile { position: relative; aspect-ratio: 1; border-radius: 2vmin; overflow: hidden; background: rgba(255,255,255,.06);
      animation: deal-in .7s cubic-bezier(.2,1.3,.4,1) both; }
    .tile img { width: 100%; height: 100%; object-fit: cover; }
    .tile-price { position: absolute; left: .6em; bottom: .6em; background: var(--accent); padding: .2em .6em; border-radius: 999px; font-weight: 800; }
    @keyframes deal-in { from { transform: translateY(30%) rotate(-6deg) scale(.8); opacity: 0; } to { transform: none; opacity: 1; } }

    .progress { position: absolute; left: 0; bottom: 0; height: .5vmin; min-height: 3px; width: 100%; z-index: 3;
      background: linear-gradient(90deg, #f9a8d4, #e11d48); transform-origin: left; animation: fill linear both; }
    @keyframes fill { from { transform: scaleX(0); } to { transform: scaleX(1); } }
    .fullscreen { position: absolute; top: 2vmin; right: 2vmin; z-index: 4; cursor: pointer; border-radius: 999px;
      background: rgba(255,255,255,.15); color: #fff; padding: .5em 1.2em; font-size: .8em; backdrop-filter: blur(8px); }
    /* Tells shoppers the screen can be touched. */
    .tap { position: absolute; left: 50%; bottom: 2vmin; z-index: 3; transform: translateX(-50%); pointer-events: none;
      padding: .3em .9em; border-radius: 999px; background: rgba(255,255,255,.12); backdrop-filter: blur(8px);
      font-size: clamp(10px, .6em, 16px); font-weight: 600; letter-spacing: .06em; white-space: nowrap; animation: breathe 2.4s ease-in-out infinite; }
    @keyframes breathe { 50% { transform: translateX(-50%) scale(1.06); background: rgba(255,255,255,.24); } }

    /* Wide screens (desktop, TV): picture beside the words. */
    @media (orientation: landscape) {
      .layout { grid-template-columns: 1.15fr 1fr; grid-template-rows: 1fr; align-items: center; padding: 6vmin 7vmin; }
      .words { grid-template-columns: 1fr; grid-template-areas: "eyebrow" "name" "prices" "qr"; align-items: start; row-gap: 1vmin; }
      .qr { flex-direction: row; justify-content: flex-start; margin-top: 3vmin; text-align: left; }
      /* Wide: the words (two groups, stacked) on the left, the pictures on the right. */
      .offer { display: grid; grid-template-columns: 1fr 1fr; grid-template-rows: auto auto; align-content: center;
        column-gap: 6vmin; row-gap: 3vmin; text-align: left; padding: 6vmin 7vmin; }
      .info { display: flex; flex-direction: column; align-items: flex-start; gap: 2.5vmin; grid-column: 1; }
      .info:empty { display: none; }
      .offer .tiles { grid-column: 2; grid-row: 1 / span 2; width: min(100%, 70vmin); justify-self: center; }
    }
    /* Narrow phones: the words a little larger against the small screen. */
    @media (max-width: 480px) and (orientation: portrait) {
      .stage { font-size: 15px; }
      .name { font-size: 2em; }
      .headline { font-size: 3em; }
    }
    @media (prefers-reduced-motion: reduce) {
      .picture img, .glow, .badge, .wordmark, .flash .headline { animation-iteration-count: 1 !important; }
    }
    .calm .glow { opacity: .2; }
  `],
})
export class ShowcasePage implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly store = inject(EcommerceStore);
  private readonly visit = inject(ShowcaseVisitService);

  protected readonly settings = signal<ApiShowcaseSettings>(DEFAULTS);
  private readonly products = signal<ApiProduct[]>([]);
  private readonly sales = signal<ApiFlashSale[]>([]);
  private readonly bundles = signal<ApiBundleOffer[]>([]);
  private readonly now = signal(Date.now());
  private readonly codes = signal<Record<string, string>>({});

  /** What plays, in order: brand, then products with an offer after every few. Switched off: brand only. */
  protected readonly slides = computed<Slide[]>(() => {
    const brand: Slide = { kind: 'brand', key: 'brand' };
    if (!this.settings().enabled) return [brand];
    const now = this.now();
    const offers: Slide[] = [
      ...this.sales().filter((s) => this.running(s, now)).map((sale) => ({ kind: 'flash' as const, key: 'flash-' + sale.id, sale })),
      ...this.bundles().filter((b) => b.isActive !== false).map((bundle) => ({ kind: 'bundle' as const, key: 'bundle-' + bundle.id, bundle })),
    ];
    const products = this.chosenProducts();
    const out: Slide[] = [brand];
    if (!products.length) return [...out, ...offers];
    let offer = 0;
    products.forEach((p, i) => {
      out.push({ kind: 'product', key: 'product-' + p.id, product: p, isNew: !!p.isNewArrival });
      if ((i + 1) % PRODUCTS_PER_OFFER === 0 && offers.length) out.push(offers[offer++ % offers.length]);
    });
    // Every offer gets its turn at least once a loop.
    while (offer < offers.length) out.push(offers[offer++]);
    return out;
  });

  /** The slide on screen, and the one sweeping out (for a moment) behind it. */
  protected readonly shown = signal<{ slide: Slide; at: number; leaving: boolean }[]>([]);
  protected readonly cycle = signal(0);
  protected readonly awake = signal(true);
  protected readonly canFullscreen = typeof document !== 'undefined' && !!document.documentElement.requestFullscreen;
  protected readonly site = typeof location !== 'undefined' ? location.host.replace(/^www\./, '') : 'erezer.com';

  private index = -1;
  private timer?: ReturnType<typeof setTimeout>;
  private ticker?: ReturnType<typeof setInterval>;
  private refresher?: ReturnType<typeof setInterval>;
  private sleepTimer?: ReturnType<typeof setTimeout>;
  private wakeLock: { release(): Promise<void> } | null = null;

  ngOnInit(): void {
    // Back on the showcase: nobody is browsing any more.
    this.visit.stop();
    this.load(true);
    this.refresher = setInterval(() => this.load(false), REFRESH_MS);
    this.ticker = setInterval(() => this.now.set(Date.now()), 1000);
    this.wake();
    void this.keepScreenOn();
    document.addEventListener('visibilitychange', this.onVisible);
  }

  ngOnDestroy(): void {
    clearTimeout(this.timer);
    clearTimeout(this.sleepTimer);
    clearInterval(this.ticker);
    clearInterval(this.refresher);
    document.removeEventListener('visibilitychange', this.onVisible);
    void this.wakeLock?.release().catch(() => undefined);
  }

  /** Everything the screen shows, asked for again every few minutes. A failed request keeps what it had. */
  private load(first: boolean): void {
    forkJoin({
      settings: this.api.getShowcase().pipe(catchError(() => of(null))),
      products: this.api.getProducts().pipe(catchError(() => of(null))),
      sales: this.api.getFlashSales().pipe(catchError(() => of(null))),
      bundles: this.api.getBundles().pipe(catchError(() => of(null))),
    }).subscribe(({ settings, products, sales, bundles }) => {
      if (settings) this.settings.set(settings);
      if (products) this.products.set(products);
      if (sales) this.sales.set(sales);
      if (bundles) this.bundles.set(bundles);
      this.makeCodes();
      if (first) this.next();
    });
  }

  /** The products the admin chose to show, available ones only. */
  private chosenProducts(): ApiProduct[] {
    const all = this.products().filter((p) => p.isAvailable && p.imageUrl);
    const s = this.settings();
    if (s.productMode === 'PICKED') {
      const byId = new Map(all.map((p) => [p.id, p]));
      return s.productIds.map((id) => byId.get(id)).filter((p): p is ApiProduct => !!p);
    }
    if (s.productMode === 'ALL') return all.slice(0, 40);
    const fresh = all.filter((p) => p.isNewArrival);
    return [...fresh, ...all.filter((p) => !p.isNewArrival)].slice(0, 20);
  }

  private running(sale: ApiFlashSale, now: number): boolean {
    const starts = sale.startsAt ? Date.parse(sale.startsAt) : 0;
    return starts <= now && Date.parse(sale.endsAt) > now && (sale.products?.length ?? 0) > 0;
  }

  /** On to the next slide; the one on screen sweeps out under it. */
  private next(): void {
    const slides = this.slides();
    this.index = slides.length ? (this.index + 1) % slides.length : 0;
    const slide = slides[this.index];
    if (slide) {
      const at = Date.now();
      this.shown.update((list) => [...list.filter((s) => !s.leaving).map((s) => ({ ...s, leaving: true })), { slide, at, leaving: false }]);
      setTimeout(() => this.shown.update((list) => list.filter((s) => !s.leaving)), HANDOVER_MS);
      this.cycle.update((n) => n + 1);
    }
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.next(), this.currentSeconds() * 1000);
  }

  /** Offers carry more to read, so they stay half as long again. */
  protected currentSeconds(): number {
    const kind = this.shown().find((s) => !s.leaving)?.slide.kind;
    const seconds = this.settings().slideSeconds || 6;
    return kind === 'flash' || kind === 'bundle' ? Math.round(seconds * 1.5) : seconds;
  }

  /** A tap on the screen opens what is showing, in the shop. */
  protected open(slide: Slide): void {
    this.visit.visit(this.pathOf(slide));
  }

  private pathOf(slide: Slide): string {
    switch (slide.kind) {
      case 'product': return '/product/' + slide.product.id;
      case 'flash': return '/flash-sale/' + slide.sale.id;
      case 'bundle': return '/bundles/' + slide.bundle.id;
      default: return '/';
    }
  }

  protected label(slide: Slide): string {
    switch (slide.kind) {
      case 'product': return slide.product.name;
      case 'flash': return 'the flash sale';
      case 'bundle': return slide.bundle.name;
      default: return 'the shop';
    }
  }

  /** What a tap does now, in the hint at the foot of the screen. */
  protected tapText(): string {
    const kind = this.shown().find((s) => !s.leaving)?.slide.kind;
    return kind === 'product' ? 'see this piece' : kind === 'flash' ? 'see the deals' : kind === 'bundle' ? 'build your bundle' : 'start shopping';
  }

  protected asProduct(s: Slide) { return s.kind === 'product' ? s : null; }
  protected asFlash(s: Slide) { return s.kind === 'flash' ? s : null; }
  protected asBundle(s: Slide) { return s.kind === 'bundle' ? s : null; }

  /** The price the shop charges, as on the product cards, with the regular one when it is lower. */
  protected priceOf(p: ApiProduct): { now: number; was: number | null; off: number } {
    const card = this.store.toStoreProduct(p);
    const was = card.originalPrice && card.originalPrice > card.price ? card.originalPrice : null;
    return { now: card.price, was, off: was ? Math.round((1 - card.price / was) * 100) : 0 };
  }

  /** As on the flash sale page. */
  protected flashPrice(sale: ApiFlashSale, p: ApiProduct): number {
    if (p.discountPrice && p.discountPrice > 0 && p.discountPrice < p.price) return p.discountPrice;
    return sale.discountType === 'PERCENT'
      ? Math.round(p.price * (1 - sale.discountValue / 100))
      : Math.max(0, p.price - sale.discountValue);
  }

  protected dealText(sale: ApiFlashSale): string {
    return sale.discountType === 'PERCENT' ? `${sale.discountValue}% off` : `৳${sale.discountValue} off`;
  }

  protected countdown(endsAt: string): { value: string; unit: string }[] {
    const left = Math.max(0, Math.floor((Date.parse(endsAt) - this.now()) / 1000));
    const days = Math.floor(left / 86400), hours = Math.floor((left % 86400) / 3600);
    const mins = Math.floor((left % 3600) / 60), secs = left % 60;
    const two = (n: number) => String(n).padStart(2, '0');
    const parts = [{ value: two(hours), unit: 'hrs' }, { value: two(mins), unit: 'min' }, { value: two(secs), unit: 'sec' }];
    return days > 0 ? [{ value: String(days), unit: days === 1 ? 'day' : 'days' }, ...parts] : parts;
  }

  /** Up to four pictures for a bundle: its own, then its products'. */
  protected bundlePictures(b: ApiBundleOffer): string[] {
    const fromProducts = (b.products ?? []).map((p) => p.imageUrl).filter((u): u is string => !!u);
    return [...(b.images ?? []), ...fromProducts].slice(0, 4);
  }

  protected link(path: string): string {
    return `${location.origin}/${path}`;
  }

  protected qr(url: string): string | null {
    return this.codes()[url] ?? null;
  }

  /** QR codes for everything that may play, made once each. */
  private makeCodes(): void {
    if (!this.settings().showQr) return;
    const wanted = new Set<string>();
    for (const s of this.slides()) {
      if (s.kind === 'product') wanted.add(this.link('product/' + s.product.id));
      if (s.kind === 'flash') wanted.add(this.link('flash-sale/' + s.sale.id));
      if (s.kind === 'bundle') wanted.add(this.link('bundles/' + s.bundle.id));
    }
    const have = this.codes();
    for (const url of wanted) {
      if (have[url]) continue;
      QRCode.toDataURL(url, { margin: 1, width: 320, errorCorrectionLevel: 'M' })
        .then((code) => this.codes.update((c) => ({ ...c, [url]: code })))
        .catch(() => undefined);
    }
  }

  /** The pointer and the full-screen button show for a few seconds after the mouse or a finger moves. */
  protected wake(): void {
    this.awake.set(true);
    clearTimeout(this.sleepTimer);
    this.sleepTimer = setTimeout(() => this.awake.set(false), 4000);
  }

  protected fullscreen(): void {
    void document.documentElement.requestFullscreen?.().catch(() => undefined);
    this.awake.set(false);
  }

  /** Asks the screen not to go to sleep while the showcase plays (where the browser allows it). */
  private async keepScreenOn(): Promise<void> {
    try {
      const nav = navigator as Navigator & { wakeLock?: { request(type: 'screen'): Promise<{ release(): Promise<void> }> } };
      this.wakeLock = (await nav.wakeLock?.request('screen')) ?? null;
    } catch {
      this.wakeLock = null;
    }
  }

  /** The lock lapses when the tab is hidden; take it again on return. */
  private onVisible = (): void => {
    if (document.visibilityState === 'visible') void this.keepScreenOn();
  };
}
