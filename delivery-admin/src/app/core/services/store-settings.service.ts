import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface SizeChartCell {
  cm: number | null;
  inch: number | null;
}

export interface SizeChartRow {
  size: string;
  cells: SizeChartCell[];
}

export interface SizeChart {
  columns: string[];
  rows: SizeChartRow[];
}

export interface BrandStory {
  eyebrow: string | null;
  heading: string | null;
  body: string | null;
  ctaLabel: string | null;
  ctaLink: string | null;
  /** The shop's social accounts, in the order shown under the story. */
  socials?: SocialLink[] | null;
  /** The first of `socials`, from when there could only be one. The server keeps it in step. */
  socialHandle: string | null;
  socialUrl: string | null;
  images: string[];
}

/** One social account: the name customers read, and where it leads. */
export interface SocialLink {
  handle: string;
  url: string | null;
}

export interface FooterLink {
  label: string;
  url: string;
}

export interface FooterColumn {
  title: string;
  links: FooterLink[];
}

export interface FooterPromise {
  icon: string;
  title: string;
  description: string;
}

export interface FooterOutlet {
  imageUrl: string;
  name: string;
  address: string;
  phone: string;
  /** The outlet's own Google Maps link; optional. */
  mapUrl?: string;
}

export interface Footer {
  brandName: string | null;
  blurb: string | null;
  columns: FooterColumn[];
  promises: FooterPromise[];
  outlets: FooterOutlet[];
  copyright: string | null;
  tagline: string | null;
}

export interface Marquee {
  /**
   * No longer used: the strip is shown or hidden on the Home Page layout
   * (HomeLayoutService). Kept, always true, because the backend still stores it.
   */
  enabled: boolean;
  items: string[];
}

export interface Highlight {
  icon: string;
  value: string;
  label: string;
  description: string;
}

export interface AboutSection {
  heading: string | null;
  body: string | null;
  imageUrl: string | null;
}

/** The storefront About Us page. */
export interface AboutPage {
  title: string | null;
  intro: string | null;
  heroImageUrl: string | null;
  sections: AboutSection[] | null;
  ctaLabel: string | null;
  ctaLink: string | null;
}

export interface StoreSettings {
  returnPolicyText: string | null;
  exchangeWindowDays: number | null;
  supportPhone: string | null;
  supportEmail: string | null;
  supportHours: string | null;
  sizeChart: SizeChart | null;
  /**
   * A size chart for each fit, keyed DROP_SHOULDER and REGULAR_FIT. A product
   * page shows the chart of the fit the customer picked; a fit with no rows of
   * its own, and a product with no fits, show sizeChart.
   */
  fitSizeCharts?: Record<string, SizeChart> | null;
  brandStory: BrandStory | null;
  aboutPage?: AboutPage | null;
  footer: Footer | null;
  marquee: Marquee | null;
  highlights: Highlight[] | null;
  paymentCodEnabled: boolean | null;
  paymentBkashEnabled: boolean | null;
  paymentCardEnabled: boolean | null;

  /**
   * Automatic-discount switches. Null means on, so a settings row saved before
   * this feature existed keeps discounting.
   *
   * These govern the rules on the Discounts screen only. A product's own sale
   * price, coupon codes, flash sales and bundle offers are separate and each
   * has its own screen.
   */
  discountsEnabled: boolean | null;
  discountsGlobalEnabled: boolean | null;
  discountsCategoryEnabled: boolean | null;
  discountsProductEnabled: boolean | null;
}

@Injectable({ providedIn: 'root' })
export class StoreSettingsService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  get(): Observable<StoreSettings> {
    return this.http.get<StoreSettings>(`${this.base}/admin/store-settings`);
  }

  update(payload: StoreSettings): Observable<StoreSettings> {
    return this.http.put<StoreSettings>(`${this.base}/admin/store-settings`, payload);
  }
}
