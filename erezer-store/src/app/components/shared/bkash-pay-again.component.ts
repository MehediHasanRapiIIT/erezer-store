import { Component, computed, inject, input, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';

/**
 * A bKash order that bKash has not been paid for yet: says so, and offers to pay
 * again, on the order page and on Track Order. Only while the order is still Placed;
 * the shop's own staff decide what happens to an order after that.
 */
@Component({
  selector: 'app-bkash-pay-again',
  standalone: true,
  template: `
    @if (show()) {
      <div class="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-amber-300 bg-amber-50 p-4 dark:border-amber-700/60 dark:bg-amber-950/40"
        role="status" data-testid="bkash-pay-again">
        <div>
          <p class="font-semibold text-amber-900 dark:text-amber-200">
            {{ paymentState() === 'FAILED' ? 'Payment failed — pay again' : 'Payment not completed — pay now' }}
          </p>
          <p class="text-sm text-amber-800/80 dark:text-amber-200/70">Your order is saved. Pay with bKash to confirm it.</p>
          @if (error()) {
            <p class="mt-1 text-sm text-red-600" role="alert">{{ error() }}</p>
          }
        </div>
        <button type="button" (click)="pay()" [disabled]="busy()" data-testid="bkash-pay-again-button"
          class="rounded-full bg-[#e2136e] px-5 py-2.5 text-sm font-semibold text-white shadow hover:opacity-90 disabled:opacity-60">
          {{ busy() ? 'Opening bKash…' : 'Pay with bKash' }}
        </button>
      </div>
    }
  `,
})
export class BkashPayAgainComponent {
  private readonly api = inject(ApiService);

  readonly orderId = input<string | null>(null);
  readonly orderNumber = input<string | null>(null);
  readonly paymentState = input<string | null | undefined>(null);
  readonly orderStatus = input<string | null | undefined>(null);

  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly show = computed(() =>
    (this.paymentState() === 'FAILED' || this.paymentState() === 'PENDING')
    && (this.orderStatus() ?? '').toUpperCase() === 'PLACED');

  protected pay(): void {
    this.busy.set(true);
    this.error.set('');
    this.api.bkashInit(this.orderId(), this.orderNumber()).subscribe({
      next: (res) => {
        if (res.bkashURL) window.location.href = res.bkashURL;
        else { this.busy.set(false); this.error.set('bKash could not start the payment. Please try again.'); }
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err?.error?.message ?? 'bKash could not start the payment. Please try again.');
      },
    });
  }
}
