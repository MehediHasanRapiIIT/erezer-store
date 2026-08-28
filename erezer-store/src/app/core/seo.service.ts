import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';

export interface SeoData {
  title: string;
  description?: string;
  image?: string;
  url?: string;
  type?: 'website' | 'product' | 'article';
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
}

const SITE_NAME = 'Erezer';
const JSONLD_ID = 'seo-jsonld';

/**
 * Centralised SEO: page title + Open Graph / Twitter meta + JSON-LD structured
 * data. Uses Angular's Title/Meta (which render server-side under SSR), so when
 * SSR is enabled these tags are present in the initial HTML for crawlers.
 */
@Injectable({ providedIn: 'root' })
export class SeoService {
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly document = inject(DOCUMENT);

  update(data: SeoData): void {
    const fullTitle = data.title.includes(SITE_NAME) ? data.title : `${data.title} · ${SITE_NAME}`;
    this.title.setTitle(fullTitle);

    const tags: Array<{ name?: string; property?: string; content: string }> = [
      { name: 'description', content: data.description ?? '' },
      { property: 'og:site_name', content: SITE_NAME },
      { property: 'og:title', content: fullTitle },
      { property: 'og:description', content: data.description ?? '' },
      { property: 'og:type', content: data.type ?? 'website' },
      { name: 'twitter:card', content: data.image ? 'summary_large_image' : 'summary' },
      { name: 'twitter:title', content: fullTitle },
      { name: 'twitter:description', content: data.description ?? '' },
    ];
    if (data.image) {
      tags.push({ property: 'og:image', content: data.image });
      tags.push({ name: 'twitter:image', content: data.image });
    }
    if (data.url) {
      tags.push({ property: 'og:url', content: data.url });
      this.setCanonical(data.url);
    }
    for (const tag of tags) {
      if (!tag.content) continue;
      const selector = tag.property ? `property='${tag.property}'` : `name='${tag.name}'`;
      this.meta.updateTag(tag as never, selector);
    }
  }

  setProductJsonLd(p: ProductJsonLd): void {
    const json = {
      '@context': 'https://schema.org/',
      '@type': 'Product',
      name: p.name,
      description: p.description,
      image: p.image,
      sku: p.sku,
      brand: p.brand ? { '@type': 'Brand', name: p.brand } : undefined,
      offers: {
        '@type': 'Offer',
        price: p.price,
        priceCurrency: p.currency ?? 'BDT',
        availability: `https://schema.org/${p.availability ?? 'InStock'}`,
        url: p.url,
      },
    };
    this.writeJsonLd(json);
  }

  /** Remove any injected JSON-LD (call when leaving a product page). */
  clearJsonLd(): void {
    const existing = this.document.getElementById(JSONLD_ID);
    if (existing) existing.remove();
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
