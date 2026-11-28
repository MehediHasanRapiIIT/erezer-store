import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Settings → Erezer Invoice. The key never comes back, only its last letters. */
export interface InvoiceAppSettings {
  url: string | null;
  keySaved: boolean;
  /** e.g. "…3PPo". */
  keyHint: string | null;
  /** An address and a key are both saved. */
  connected: boolean;
}

export interface InvoiceAppChange {
  /** Empty clears it. */
  url?: string;
  /** A new key; leave out to keep the saved one. */
  key?: string;
  removeKey?: boolean;
}

export type InvoiceSendOutcome = 'SENT' | 'ALREADY_THERE' | 'FAILED';

/** What became of one order. */
export interface InvoiceSendResult {
  orderId: string;
  orderNumber: string | null;
  outcome: InvoiceSendOutcome;
  /** Its id in Erezer Invoice. */
  invoiceOrderId: number | null;
  /** Why it wasn't sent. */
  message: string | null;
  /** Product photos that couldn't go with it. */
  missingPhotos: number;
}

/** "In Invoice ✓": the order reached Erezer Invoice. */
export interface InvoiceSentMark {
  invoiceOrderId: number | null;
  sentAt: string;
  sentBy: string | null;
  missingPhotos: number;
}

/** The most orders one send takes (the server's limit too). */
export const MAX_INVOICE_SEND = 50;

@Injectable({ providedIn: 'root' })
export class InvoiceAppService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/admin/invoice-app`;

  get(): Observable<InvoiceAppSettings> {
    return this.http.get<InvoiceAppSettings>(this.base);
  }

  update(change: InvoiceAppChange): Observable<InvoiceAppSettings> {
    return this.http.put<InvoiceAppSettings>(this.base, change);
  }

  /** Asks Erezer Invoice whether it accepts the saved key; changes nothing there. */
  test(): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.base}/test`, {});
  }

  /** Whether there is anywhere to send to (needs "Send orders to Erezer Invoice"). */
  status(): Observable<{ connected: boolean }> {
    return this.http.get<{ connected: boolean }>(`${this.base}/status`);
  }

  /** Sends the orders, with their photos; one result per order, in the same order. */
  send(orderIds: string[]): Observable<InvoiceSendResult[]> {
    return this.http.post<InvoiceSendResult[]>(`${this.base}/send`, { orderIds });
  }

  /** The "In Invoice ✓" marks for these orders; orders never sent are left out. */
  sent(orderIds: string[]): Observable<Record<string, InvoiceSentMark>> {
    return this.http.get<Record<string, InvoiceSentMark>>(`${this.base}/sent`, { params: { ids: orderIds.join(',') } });
  }
}
