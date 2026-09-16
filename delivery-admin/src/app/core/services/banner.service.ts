import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BannerContent, BannerResponse, BannerSlot, PageResponse } from '../models/api.models';

/** What one home-page spot holds: its banner count and first two banners in display order. */
export interface BannerSlotSummary {
  slot: BannerSlot;
  count: number;
  first: BannerResponse[];
}

@Injectable({ providedIn: 'root' })
export class BannerService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  getBanners(): Observable<BannerResponse[]> {
    return this.http.get<BannerResponse[]>(`${this.baseUrl}/api/banners`);
  }

  /** One page for the admin Banners page, in home-page order; blank slot means every spot. */
  getBannerPage(q: string, slot: BannerSlot | '', page: number, size: number): Observable<PageResponse<BannerResponse>> {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (q) params['q'] = q;
    if (slot) params['slot'] = slot;
    return this.http.get<PageResponse<BannerResponse>>(`${this.baseUrl}/api/banners/paged`, { params });
  }

  /** Every spot's banner count and first banners, for the map of spots and the empty-band warning. */
  getSlotSummary(): Observable<BannerSlotSummary[]> {
    return this.http.get<BannerSlotSummary[]>(`${this.baseUrl}/api/banners/slots`);
  }

  /** Active banners for one landing-page band, in display order. */
  getBannersForSlot(slot: BannerSlot): Observable<BannerResponse[]> {
    return this.http.get<BannerResponse[]>(`${this.baseUrl}/api/banners/slot/${slot}`);
  }

  uploadBanner(image: File, content: BannerContent = {}): Observable<BannerResponse> {
    const formData = this.toFormData(content);
    formData.append('image', image);
    return this.http.post<BannerResponse>(`${this.baseUrl}/api/banners`, formData);
  }

  updateBanner(id: string, image: File | undefined, content: BannerContent = {}): Observable<BannerResponse> {
    const formData = this.toFormData(content);
    if (image) formData.append('image', image);
    return this.http.put<BannerResponse>(`${this.baseUrl}/api/banners/${id}`, formData);
  }

  deleteBanner(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/banners/${id}`);
  }

  /**
   * The endpoints are multipart, so every field goes on the form individually.
   *
   * Undefined and empty values are skipped rather than sent blank: on update the
   * backend reads a missing field as "leave unchanged", so sending "" would wipe
   * a title the user never touched.
   */
  private toFormData(content: BannerContent): FormData {
    const form = new FormData();
    for (const [key, value] of Object.entries(content)) {
      if (value === undefined || value === null || value === '') continue;
      form.append(key, String(value));
    }
    return form;
  }
}
