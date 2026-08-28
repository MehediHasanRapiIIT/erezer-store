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

  list(status?: string, page = 0, size = 30): Observable<PageResponse<ContactMessage>> {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (status && status !== 'ALL') params['status'] = status;
    return this.http.get<PageResponse<ContactMessage>>(
      `${this.base}/admin/support/messages`, { params });
  }

  updateStatus(id: string, status: ContactStatus): Observable<ContactMessage> {
    return this.http.patch<ContactMessage>(
      `${this.base}/admin/support/messages/${id}`, { status });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/support/messages/${id}`);
  }
}
