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
  socialHandle: string | null;
  socialUrl: string | null;
  images: string[];
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
  enabled: boolean;
  items: string[];
}

export interface Highlight {
  icon: string;
  value: string;
  label: string;
  description: string;
}

export interface StoreSettings {
  returnPolicyText: string | null;
  exchangeWindowDays: number | null;
  supportPhone: string | null;
  supportEmail: string | null;
  supportHours: string | null;
  sizeChart: SizeChart | null;
  brandStory: BrandStory | null;
  footer: Footer | null;
  marquee: Marquee | null;
  highlights: Highlight[] | null;
  paymentCodEnabled: boolean | null;
  paymentBkashEnabled: boolean | null;
  paymentCardEnabled: boolean | null;
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
