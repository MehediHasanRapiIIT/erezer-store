import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type SubscriberStatus = 'SUBSCRIBED' | 'UNSUBSCRIBED';
export type CampaignStatus   = 'DRAFT' | 'SENDING' | 'SENT' | 'FAILED';
export type CampaignAudience = 'ALL_SUBSCRIBERS' | 'REGISTERED_CUSTOMERS';

export interface NewsletterSubscriber {
  id: string;
  email: string;
  status: SubscriberStatus;
  source: string | null;
  subscribedAt: string;
  unsubscribedAt: string | null;
}

export interface NewsletterCampaign {
  id: string;
  subject: string;
  bodyHtml: string;
  audience: CampaignAudience;
  status: CampaignStatus;
  sentAt: string | null;
  sentBy: string | null;
  sentCount: number;
  failCount: number;
  createdAt: string;
}

export interface CampaignRequest {
  subject: string;
  bodyHtml: string;
  audience?: CampaignAudience;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class AdminNewsletterService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  /** One page of subscribers, newest first. `q` searches the email. */
  listSubscribers(status?: string, page = 0, size = 50, q?: string): Observable<PageResponse<NewsletterSubscriber>> {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (status && status !== 'ALL') params['status'] = status;
    if (q?.trim()) params['q'] = q.trim();
    return this.http.get<PageResponse<NewsletterSubscriber>>(
      `${this.base}/admin/newsletter/subscribers`, { params });
  }

  activeCount(): Observable<number> {
    return this.http.get<number>(`${this.base}/admin/newsletter/subscribers/count`);
  }

  /** One page of campaigns, newest first. `q` searches the subject. */
  listCampaigns(page = 0, size = 20, q?: string): Observable<PageResponse<NewsletterCampaign>> {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (q?.trim()) params['q'] = q.trim();
    return this.http.get<PageResponse<NewsletterCampaign>>(
      `${this.base}/admin/newsletter/campaigns`, { params });
  }

  getCampaign(id: string): Observable<NewsletterCampaign> {
    return this.http.get<NewsletterCampaign>(`${this.base}/admin/newsletter/campaigns/${id}`);
  }

  createCampaign(payload: CampaignRequest): Observable<NewsletterCampaign> {
    return this.http.post<NewsletterCampaign>(`${this.base}/admin/newsletter/campaigns`, payload);
  }

  updateCampaign(id: string, payload: CampaignRequest): Observable<NewsletterCampaign> {
    return this.http.put<NewsletterCampaign>(`${this.base}/admin/newsletter/campaigns/${id}`, payload);
  }

  send(id: string): Observable<NewsletterCampaign> {
    return this.http.post<NewsletterCampaign>(`${this.base}/admin/newsletter/campaigns/${id}/send`, {});
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/newsletter/campaigns/${id}`);
  }
}
