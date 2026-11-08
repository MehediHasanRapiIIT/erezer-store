import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NoticeService } from '../../../core/services/notice.service';
import { PermissionService } from '../../../core/services/permission.service';
import { parseApiError } from '../../../core/utils/api-error.util';

/** What the server says about an order's bKash refunds (BkashRefundService.Summary). */
interface RefundSummary {
  paid: number;
  refunded: number;
  refundable: number;
  refundableUntil: string | null;
  allowed: boolean;
  notAllowedReason: string | null;
  trxId: string | null;
  refunds: {
    status: 'COMPLETED' | 'FAILED' | 'UNKNOWN';
    amount: number;
    reason: string;
    refundTrxId: string | null;
    message: string | null;
    requestedBy: string | null;
    at: string;
    fromReturn: boolean;
  }[];
}

/**
 * On a bKash-paid order: what was paid, what has gone back, and a refund to the
 * customer's bKash wallet (all or part), for staff who may refund returns.
 */
@Component({
  selector: 'app-bkash-refund',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="bg-white rounded-xl border border-gray-200 p-5" data-testid="bkash-refund">
      <header class="mb-3">
        <h2 class="font-bold text-gray-900 text-sm">bKash refund</h2>
        <p class="text-xs text-gray-500">Money goes back to the wallet that paid. Up to {{ maxRefunds }} refunds, within 60 days of the payment.</p>
      </header>

      @if (summary(); as s) {
        <div class="grid grid-cols-3 gap-3 text-sm">
          <div><p class="text-xs text-gray-400">Paid</p><p class="font-semibold">৳{{ money(s.paid) }}</p></div>
          <div><p class="text-xs text-gray-400">Refunded</p><p class="font-semibold" data-testid="bkash-refunded">৳{{ money(s.refunded) }}</p></div>
          <div><p class="text-xs text-gray-400">Can still refund</p><p class="font-semibold text-emerald-700" data-testid="bkash-refundable">৳{{ money(s.refundable) }}</p></div>
        </div>

        @if (s.refunds.length) {
          <ul class="mt-4 divide-y divide-gray-100 rounded-lg border border-gray-100 text-xs" data-testid="bkash-refund-list">
            @for (r of s.refunds; track $index) {
              <li class="flex flex-wrap items-baseline justify-between gap-2 px-3 py-2">
                <span>
                  <strong>৳{{ money(r.amount) }}</strong> · {{ r.reason }}
                  @if (r.fromReturn) { <span class="text-gray-400">(return)</span> }
                  <span class="block text-gray-400">{{ when(r.at) }} · {{ r.requestedBy }}</span>
                  @if (r.message) { <span class="block text-red-600">{{ r.message }}</span> }
                </span>
                @switch (r.status) {
                  @case ('COMPLETED') {
                    <span class="rounded-full bg-emerald-50 px-2 py-0.5 font-semibold text-emerald-700">Refunded · <span class="font-mono select-all">{{ r.refundTrxId }}</span></span>
                  }
                  @case ('UNKNOWN') {
                    <span class="rounded-full bg-amber-50 px-2 py-0.5 font-semibold text-amber-700">No answer from bKash — check the merchant account</span>
                  }
                  @default {
                    <span class="rounded-full bg-red-50 px-2 py-0.5 font-semibold text-red-700">Not refunded</span>
                  }
                }
              </li>
            }
          </ul>
        }

        @if (!s.allowed) {
          <p class="mt-4 rounded-md bg-gray-50 px-3 py-2 text-xs text-gray-600" data-testid="bkash-refund-blocked">{{ s.notAllowedReason }}</p>
        } @else if (!perms.can('returns.refund')) {
          <p class="mt-4 text-xs text-gray-400">Needs the “Refund returns” permission.</p>
        } @else {
          <div class="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-[140px_1fr_auto] sm:items-end">
            <label class="text-xs font-medium text-gray-600">
              Amount (৳)
              <input type="number" min="1" [max]="s.refundable" step="0.01" [(ngModel)]="amount" data-testid="bkash-refund-amount"
                class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
            </label>
            <label class="text-xs font-medium text-gray-600">
              Reason
              <input [(ngModel)]="reason" maxlength="255" placeholder="e.g. Out of stock, order cancelled" data-testid="bkash-refund-reason"
                class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
            </label>
            <button type="button" (click)="refund()" [disabled]="busy() || !amount" data-testid="bkash-refund-button"
              class="rounded-lg bg-[#e2136e] px-4 py-2 text-sm font-semibold text-white hover:opacity-90 disabled:opacity-50">
              {{ busy() ? 'Refunding…' : 'Refund with bKash' }}
            </button>
          </div>
          @if (s.refundableUntil) {
            <p class="mt-2 text-xs text-gray-400">bKash accepts refunds until {{ when(s.refundableUntil) }}.</p>
          }
        }
        @if (error()) {
          <p class="mt-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert" data-testid="bkash-refund-error">{{ error() }}</p>
        }
      } @else if (error()) {
        <p class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{{ error() }}</p>
      } @else {
        <p class="text-sm text-gray-400">Loading…</p>
      }
    </section>
  `,
})
export class BkashRefundComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly confirm = inject(ConfirmService);
  private readonly notices = inject(NoticeService);
  protected readonly perms = inject(PermissionService);

  readonly orderId = input.required<string>();
  /** A refund went through: the page may want to reload the order. */
  readonly refunded = output<void>();

  protected readonly maxRefunds = 10;
  protected readonly summary = signal<RefundSummary | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected amount: number | null = null;
  protected reason = '';

  private url(): string {
    return `${environment.apiBaseUrl}/admin/orders/${this.orderId()}/bkash-refunds`;
  }

  ngOnInit(): void {
    this.http.get<RefundSummary>(this.url()).subscribe({
      next: (s) => this.show(s),
      error: (err: HttpErrorResponse) => this.error.set(parseApiError(err)),
    });
  }

  private show(s: RefundSummary): void {
    this.summary.set(s);
    this.amount = s.allowed ? Number(s.refundable) : null;
  }

  protected async refund(): Promise<void> {
    const amount = Number(this.amount);
    if (!amount || amount <= 0) return;
    const ok = await this.confirm.ask({
      title: `Refund ৳${this.money(amount)} to the customer's bKash?`,
      message: 'bKash sends the money back to the wallet that paid. This cannot be undone.',
      confirmLabel: 'Refund',
      danger: true,
    });
    if (!ok) return;
    this.busy.set(true);
    this.error.set('');
    this.http.post<RefundSummary>(this.url(), { amount, reason: this.reason.trim() || null }).subscribe({
      next: (s) => {
        this.busy.set(false);
        this.reason = '';
        this.show(s);
        this.notices.success('Refunded with bKash', `৳${this.money(amount)} is on its way back to the customer.`);
        this.refunded.emit();
      },
      error: (err: HttpErrorResponse) => {
        this.busy.set(false);
        this.error.set(parseApiError(err));
        // The list shows the refused try too.
        this.http.get<RefundSummary>(this.url()).subscribe({ next: (s) => this.summary.set(s) });
      },
    });
  }

  protected money(n: number | null | undefined): string {
    return Number(n ?? 0).toLocaleString('en-BD', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  protected when(at: string | null): string {
    if (!at) return '';
    const d = new Date(at);
    return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
      + ', ' + d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', hour12: true });
  }
}
