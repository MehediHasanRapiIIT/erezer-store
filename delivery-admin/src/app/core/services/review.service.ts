import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse, RatingSummary, ReviewResponse } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class ReviewService {
  private http = inject(HttpClient);
  private baseUrl = environment.apiBaseUrl;

  getReviews(productId: number, page = 0, size = 10): Observable<PageResponse<ReviewResponse>> {
    return this.http.get<PageResponse<ReviewResponse>>(
      `${this.baseUrl}/admin/products/${productId}/reviews?page=${page}&size=${size}`
    );
  }

  getRatingSummary(productId: number): Observable<RatingSummary> {
    return this.http.get<RatingSummary>(
      `${this.baseUrl}/admin/products/${productId}/reviews/summary`
    );
  }

  deleteReview(productId: number, reviewId: string): Observable<void> {
    return this.http.delete<void>(
      `${this.baseUrl}/admin/products/${productId}/reviews/${reviewId}`
    );
  }
}
