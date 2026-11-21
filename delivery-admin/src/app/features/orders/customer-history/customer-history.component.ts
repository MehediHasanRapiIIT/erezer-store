import { Component, computed, inject, input, OnChanges, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CustomerHistory } from '../../../core/models/api.models';
import { OrderService } from '../../../core/services/order.service';
import { parseApiError } from '../../../core/utils/api-error.util';

/**
 * How a customer has ordered before, from the same phone number however it was
 * typed: green for an old customer whose orders arrived, red when earlier orders
 * were cancelled or returned, grey for a first order.
 */
@Component({
  selector: 'app-customer-history-badge',
  standalone: true,
  template: `
    @if (history(); as h) {
      <span [class]="'mt-1 inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-semibold ' + tone()"
        [title]="title()" data-testid="customer-history-badge">
        {{ text() }}
      </span>
    }
  `,
})
export class CustomerHistoryBadgeComponent {
  readonly history = input<CustomerHistory | undefined>();

  protected readonly text = computed(() => describe(this.history()));
  protected readonly tone = computed(() => toneOf(this.history()));
  protected readonly title = computed(() => {
    const h = this.history();
    return h && total(h) > 0
      ? `Other orders from this phone number: ${total(h)}`
      : 'No other orders from this phone number';
  });
}

/**
 * The order page's "Customer history": the counts, then every other order from
 * the same phone number, newest first. Each opens in a new tab so this order
 * stays open.
 */
@Component({
  selector: 'app-customer-history',
  standalone: true,
  imports: [DatePipe, RouterLink, CustomerHistoryBadgeComponent],
  template: `
    <section class="bg-white rounded-xl border border-gray-200 p-5" data-testid="customer-history">
      <header class="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 class="font-bold text-gray-900 text-sm">Customer history</h2>
          <p class="text-xs text-gray-500">Other orders from this phone number, however it was typed.</p>
        </div>
        @if (history(); as h) {
          <app-customer-history-badge [history]="h" />
        }
      </header>

      @if (error()) {
        <p class="rounded-md bg-red-50 px-3 py-2 text-xs text-red-700">{{ error() }}</p>
      } @else if (loading()) {
        <p class="text-xs text-gray-400">Loading…</p>
      } @else if (history(); as h) {
        @if (h.orders.length === 0) {
          <p class="text-xs text-gray-500">This is the first order from this phone number.</p>
        } @else {
          <div class="mb-3 grid grid-cols-4 gap-2 text-center">
            <div class="rounded-lg bg-emerald-50 py-2"><p class="text-lg font-bold text-emerald-700">{{ h.delivered }}</p><p class="text-[11px] text-emerald-700">Delivered</p></div>
            <div class="rounded-lg bg-red-50 py-2"><p class="text-lg font-bold text-red-700">{{ h.cancelled }}</p><p class="text-[11px] text-red-700">Cancelled</p></div>
            <div class="rounded-lg bg-rose-50 py-2"><p class="text-lg font-bold text-rose-700">{{ h.returned }}</p><p class="text-[11px] text-rose-700">Returned</p></div>
            <div class="rounded-lg bg-gray-50 py-2"><p class="text-lg font-bold text-gray-700">{{ h.active }}</p><p class="text-[11px] text-gray-600">On the way</p></div>
          </div>
          <ul class="divide-y divide-gray-100 rounded-lg border border-gray-100">
            @for (o of h.orders; track o.id) {
              <li>
                <a [routerLink]="['/orders', o.id]" target="_blank" rel="noopener"
                  class="flex items-center justify-between gap-3 px-3 py-2 text-xs hover:bg-gray-50" data-testid="customer-history-order">
                  <span class="min-w-0">
                    <span class="font-mono font-semibold text-gray-800">{{ o.orderNumber || o.id.slice(0, 8) }}</span>
                    <span class="ml-2 text-gray-400">{{ o.createdAt | date: 'd MMM y' }}</span>
                  </span>
                  <span class="flex shrink-0 items-center gap-2">
                    <span class="font-semibold text-gray-700">৳ {{ amount(o.totalAmount) }}</span>
                    <span [class]="'rounded-full px-2 py-0.5 text-[11px] font-semibold ' + statusClass(o.orderStatus)">{{ statusLabel(o.orderStatus) }}</span>
                  </span>
                </a>
              </li>
            }
          </ul>
        }
      }
    </section>
  `,
})
export class CustomerHistoryComponent implements OnChanges {
  private readonly orders = inject(OrderService);

  readonly orderId = input.required<string>();

  protected readonly history = signal<CustomerHistory | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal('');

  ngOnChanges(): void {
    this.loading.set(true);
    this.error.set('');
    this.orders.getCustomerHistory(this.orderId()).subscribe({
      next: (h) => {
        this.history.set(h);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(parseApiError(err));
        this.loading.set(false);
      },
    });
  }

  protected amount(v: number): string {
    return Number(v).toLocaleString('en-BD');
  }

  protected statusLabel(s: string): string {
    return s?.replace(/_/g, ' ') ?? '—';
  }

  protected statusClass(s: string): string {
    switch (s?.toUpperCase()) {
      case 'DELIVERED': return 'bg-emerald-50 text-emerald-700';
      case 'CANCELLED': return 'bg-red-50 text-red-700';
      case 'RETURNED':  return 'bg-rose-50 text-rose-700';
      default:          return 'bg-gray-100 text-gray-600';
    }
  }
}

function total(h: CustomerHistory): number {
  return h.delivered + h.cancelled + h.returned + h.active;
}

/** "5 delivered", "2 cancelled · 1 returned", "New customer". */
export function describe(h: CustomerHistory | undefined | null): string {
  if (!h || total(h) === 0) return 'New customer';
  const parts: string[] = [];
  if (h.delivered) parts.push(`${h.delivered} delivered`);
  if (h.cancelled) parts.push(`${h.cancelled} cancelled`);
  if (h.returned) parts.push(`${h.returned} returned`);
  if (h.active) parts.push(`${h.active} on the way`);
  return parts.join(' · ');
}

/**
 * Green: earlier orders arrived and none failed. Amber: mostly arrived, some
 * cancelled or returned. Red: as many failed as arrived, or more. Grey: nothing
 * finished yet.
 */
function toneOf(h: CustomerHistory | undefined | null): string {
  if (!h || total(h) === 0) return 'bg-gray-100 text-gray-600';
  const failed = h.cancelled + h.returned;
  if (failed > 0 && failed >= h.delivered) return 'bg-red-50 text-red-700 border border-red-200';
  if (failed > 0) return 'bg-amber-50 text-amber-700 border border-amber-200';
  if (h.delivered > 0) return 'bg-emerald-50 text-emerald-700 border border-emerald-200';
  return 'bg-gray-100 text-gray-600';
}
