import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BannerContent, BannerResponse, BannerSlot } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class BannerService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  getBanners(): Observable<BannerResponse[]> {
    return this.http.get<BannerResponse[]>(`${this.baseUrl}/api/banners`);
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
