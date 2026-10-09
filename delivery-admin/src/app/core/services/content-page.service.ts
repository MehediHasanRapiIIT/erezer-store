import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** One section of a page: a heading, its text and an optional picture. */
export interface ContentPageSection {
  heading: string | null;
  body: string | null;
  imageUrl: string | null;
}

/** One of the shop's own pages ("Our Mission", "Our Values"), read by customers at /pages/<slug>. */
export interface ContentPage {
  id: number | null;
  /** The last part of the page's address. Left empty, it is made from the title. */
  slug: string;
  title: string;
  /** A small line above the title. */
  eyebrow: string | null;
  /** The opening. An empty line starts a new paragraph; the first paragraph is shown large. */
  intro: string | null;
  heroImageUrl: string | null;
  sections: ContentPageSection[];
  /** The line the page ends on. */
  closing: string | null;
  ctaLabel: string | null;
  ctaLink: string | null;
  /** Linked from the footer. */
  showInFooter: boolean;
  /** Switched off, customers can't open it. */
  isActive: boolean;
  sortOrder?: number;
}

/** The shop's own pages. Changing them needs the "Edit the About page" permission. */
@Injectable({ providedIn: 'root' })
export class ContentPageService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  /** Every page, published or not. */
  list(): Observable<ContentPage[]> {
    return this.http.get<ContentPage[]>(`${this.base}/admin/pages`);
  }

  create(page: ContentPage): Observable<ContentPage> {
    return this.http.post<ContentPage>(`${this.base}/admin/pages`, page);
  }

  update(id: number, page: ContentPage): Observable<ContentPage> {
    return this.http.put<ContentPage>(`${this.base}/admin/pages/${id}`, page);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/pages/${id}`);
  }
}
