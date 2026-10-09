import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { ResolveEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

export interface SeoData {
  title: string;
  description?: string;
  image?: string;
  url?: string;
  type?: 'website' | 'product' | 'article';
  /** A private page (cart, checkout, account): ask search engines not to list it. */
  noindex?: boolean;
}

/** What a route may put in its `data.seo` (see app.routes.ts). */
export interface RouteSeo {
  title?: string;
  description?: string;
  noindex?: boolean;
  /** The home page: also tell search engines who the shop is. */
  shop?: boolean;
}

export interface ProductJsonLd {
  name: string;
  description?: string;
  image?: string;
  sku?: string;
  brand?: string;
  price: number;
  currency?: string;
  availability?: 'InStock' | 'OutOfStock';
  url?: string;
  /** Average of the customer reviews, with how many there are; left out when there are none. */
  rating?: { value: number; count: number };
}

const SITE_NAME = 'Erezer';
/** The home page's title, and what any page without its own falls back to. Also in index.html. */
export const SITE_TITLE = 'Erezer – Clothing & Custom T-Shirts in Bangladesh';
/** Also in index.html. */
export const SITE_DESCRIPTION =
  'Erezer is a clothing shop in Bangladesh: t-shirts, hoodies and more, plus custom t-shirts printed with your own design. Cash on delivery and bKash, delivery across Bangladesh.';
/** Shown when a link is shared and the page has no picture of its own (public/og-image.jpg). */
const SHARE_IMAGE = '/og-image.jpg';
const LOGO = '/logo.png';
const JSONLD_ID = 'seo-jsonld';

/**
 * Centralised SEO: page title + description + Open Graph / Twitter meta +
 * canonical address + JSON-LD structured data.
 *
 * Every navigation first gets the defaults its route declares (or the shop's
 * own), so nothing of the previous page is left behind; a page that knows more
 * (a product, a collection) then calls {@link update} with its own.
 */
@Injectable({ providedIn: 'root' })
export class SeoService {
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly document = inject(DOCUMENT);
  private readonly router = inject(Router);

  /**
   * Starts applying each route's defaults. ResolveEnd is the last router event
   * before the page component is created, so a page that sets its own details
   * in its constructor still has the final word.
   */
  followRoutes(): void {
    this.router.events.pipe(filter((e): e is ResolveEnd => e instanceof ResolveEnd)).subscribe((e) => {
      let route = e.state.root;
      while (route.firstChild) route = route.firstChild;
      const seo = (route.data['seo'] ?? {}) as RouteSeo;
      const url = this.origin() + (e.urlAfterRedirects.split(/[?#]/)[0] || '/');
      this.clearJsonLd();
      this.update({ title: seo.title ?? SITE_TITLE, description: seo.description, noindex: seo.noindex, url });
      if (seo.shop) this.setShopJsonLd();
    });
  }

  update(data: SeoData): void {
    const fullTitle = data.title.includes(SITE_NAME) ? data.title : `${data.title} · ${SITE_NAME}`;
    this.title.setTitle(fullTitle);

    const description = this.plain(data.description) || SITE_DESCRIPTION;
    const image = this.absolute(data.image || SHARE_IMAGE);
    const url = data.url || this.origin() + this.document.location.pathname;

    const tags: Array<{ name?: string; property?: string; content: string }> = [
      { name: 'description', content: description },
      { name: 'robots', content: data.noindex ? 'noindex, nofollow' : 'index, follow' },
      { property: 'og:site_name', content: SITE_NAME },
      { property: 'og:title', content: fullTitle },
      { property: 'og:description', content: description },
      { property: 'og:type', content: data.type ?? 'website' },
      { property: 'og:image', content: image },
      { property: 'og:url', content: url },
      { name: 'twitter:card', content: 'summary_large_image' },
      { name: 'twitter:title', content: fullTitle },
      { name: 'twitter:description', content: description },
      { name: 'twitter:image', content: image },
    ];
    for (const tag of tags) {
      const selector = tag.property ? `property='${tag.property}'` : `name='${tag.name}'`;
      this.meta.updateTag(tag as never, selector);
    }
    this.setCanonical(url);
  }

  setProductJsonLd(p: ProductJsonLd): void {
    const json = {
      '@context': 'https://schema.org/',
      '@type': 'Product',
      name: p.name,
      description: this.plain(p.description) || undefined,
      image: p.image ? this.absolute(p.image) : undefined,
      sku: p.sku || undefined,
      brand: { '@type': 'Brand', name: p.brand || SITE_NAME },
      aggregateRating: p.rating && p.rating.count > 0
        ? { '@type': 'AggregateRating', ratingValue: p.rating.value, reviewCount: p.rating.count }
        : undefined,
      offers: {
        '@type': 'Offer',
        price: Math.round(p.price * 100) / 100,
        priceCurrency: p.currency ?? 'BDT',
        availability: `https://schema.org/${p.availability ?? 'InStock'}`,
        itemCondition: 'https://schema.org/NewCondition',
        url: p.url,
        seller: { '@type': 'Organization', name: SITE_NAME },
      },
    };
    this.writeJsonLd(json);
  }

  /** Who the shop is: its name, logo and address, and that this is its website. */
  setShopJsonLd(): void {
    const home = this.origin() + '/';
    this.writeJsonLd({
      '@context': 'https://schema.org',
      '@graph': [
        {
          '@type': ['Organization', 'OnlineStore'],
          '@id': home + '#shop',
          name: SITE_NAME,
          url: home,
          logo: this.absolute(LOGO),
          image: this.absolute(SHARE_IMAGE),
          description: SITE_DESCRIPTION,
          areaServed: { '@type': 'Country', name: 'Bangladesh' },
        },
        {
          '@type': 'WebSite',
          '@id': home + '#website',
          name: SITE_NAME,
          url: home,
          publisher: { '@id': home + '#shop' },
          inLanguage: ['en', 'bn'],
        },
      ],
    });
  }

  /** Remove any injected JSON-LD (call when leaving a product page). */
  clearJsonLd(): void {
    const existing = this.document.getElementById(JSONLD_ID);
    if (existing) existing.remove();
  }

  private origin(): string {
    return this.document.location?.origin ?? '';
  }

  /** A site-relative address made absolute, as sharing previews and search engines need. */
  private absolute(url: string): string {
    return /^https?:\/\//i.test(url) ? url : this.origin() + (url.startsWith('/') ? url : '/' + url);
  }

  /** One line of plain text, short enough for a search result. */
  private plain(text: string | undefined | null): string {
    const line = (text ?? '').replace(/\s+/g, ' ').trim();
    return line.length > 180 ? line.slice(0, 177).trimEnd() + '…' : line;
  }

  private writeJsonLd(data: unknown): void {
    this.clearJsonLd();
    const script = this.document.createElement('script');
    script.id = JSONLD_ID;
    script.type = 'application/ld+json';
    script.text = JSON.stringify(data);
    this.document.head.appendChild(script);
  }

  private setCanonical(url: string): void {
    let link = this.document.querySelector("link[rel='canonical']") as HTMLLinkElement | null;
    if (!link) {
      link = this.document.createElement('link');
      link.setAttribute('rel', 'canonical');
      this.document.head.appendChild(link);
    }
    link.setAttribute('href', url);
  }
}
