import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface OrderNote {
  id: string;
  orderId: string;
  body: string;
  author: string | null;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class OrderNoteService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  list(orderId: string): Observable<OrderNote[]> {
    return this.http.get<OrderNote[]>(`${this.base}/admin/orders/${orderId}/notes`);
  }

  create(orderId: string, body: string): Observable<OrderNote> {
    return this.http.post<OrderNote>(`${this.base}/admin/orders/${orderId}/notes`, { body });
  }

  delete(orderId: string, noteId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/orders/${orderId}/notes/${noteId}`);
  }
}
