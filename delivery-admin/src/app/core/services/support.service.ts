import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type ContactStatus = 'NEW' | 'READ' | 'RESOLVED';

export interface ContactMessage {
  id: string;
  name: string;
  email: string;
  subject: string | null;
  message: string;
  status: ContactStatus;
  orderId: string | null;
  createdAt: string;
  /** The replies the shop has sent from here, oldest first. */
  replies?: ContactReply[];
}

/** One reply sent to the customer from the admin panel. */
export interface ContactReply {
  id: number;
  body: string;
  /** Who sent it. */
  sentByName: string | null;
  sentAt: string | null;
  /** The address it went to. */
  sentTo: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class SupportService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  /** One page of messages, newest first. `q` searches name, email, subject and message. */
  list(status?: string, page = 0, size = 30, q?: string): Observable<PageResponse<ContactMessage>> {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (status && status !== 'ALL') params['status'] = status;
    if (q?.trim()) params['q'] = q.trim();
    return this.http.get<PageResponse<ContactMessage>>(
      `${this.base}/admin/support/messages`, { params });
  }

  updateStatus(id: string, status: ContactStatus): Observable<ContactMessage> {
    return this.http.patch<ContactMessage>(
      `${this.base}/admin/support/messages/${id}`, { status });
  }

  /**
   * Emails this reply to the customer from the shop's own address and keeps it
   * under the message, which becomes RESOLVED. Refused, and nothing kept, when
   * the email could not be sent.
   */
  reply(id: string, body: string): Observable<ContactMessage> {
    return this.http.post<ContactMessage>(`${this.base}/admin/support/messages/${id}/reply`, { body });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/support/messages/${id}`);
  }
}
