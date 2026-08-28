import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BannerResponse } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class BannerService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  getBanners(): Observable<BannerResponse[]> {
    return this.http.get<BannerResponse[]>(`${this.baseUrl}/api/banners`);
  }

  uploadBanner(
    image: File,
    promotionTitle?: string,
    promotionDetails?: string,
    fromDate?: string,
    toDate?: string
  ): Observable<BannerResponse> {
    const formData = new FormData();
    formData.append('image', image);
    if (promotionTitle) formData.append('promotionTitle', promotionTitle);
    if (promotionDetails) formData.append('promotionDetails', promotionDetails);
    if (fromDate) formData.append('fromDate', fromDate);
    if (toDate) formData.append('toDate', toDate);
    return this.http.post<BannerResponse>(`${this.baseUrl}/api/banners`, formData);
  }

  updateBanner(
    id: string,
    image?: File,
    promotionTitle?: string,
    promotionDetails?: string,
    fromDate?: string,
    toDate?: string
  ): Observable<BannerResponse> {
    const formData = new FormData();
    if (image) formData.append('image', image);
    if (promotionTitle !== undefined) formData.append('promotionTitle', promotionTitle);
    if (promotionDetails !== undefined) formData.append('promotionDetails', promotionDetails);
    if (fromDate) formData.append('fromDate', fromDate);
    if (toDate) formData.append('toDate', toDate);
    return this.http.put<BannerResponse>(`${this.baseUrl}/api/banners/${id}`, formData);
  }

  deleteBanner(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/banners/${id}`);
  }
}
