import { Component, inject, OnInit, signal } from '@angular/core';
import { PixelService } from '../core/pixel.service';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ApiService } from '../core/api.service';
import { BkashPaymentResponse } from '../core/api.models';

type State = 'verifying' | 'success' | 'failed' | 'cancelled' | 'invalid';

/**
 * Storefront landing page after the bKash hosted checkout redirect (or, in
 * STUB mode, the simulated jump). Reads `paymentId` + `status` query params,
 * calls back into our `/api/payments/bkash/execute` to capture the payment,
 * then forwards the customer to their order detail page.
 */
@Component({
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="mx-auto max-w-md py-16">
      <article class="app-card space-y-4 p-8 text-center">
        <h1 class="text-2xl font-semibold">bKash payment</h1>

        @switch (state()) {
          @case ('verifying') {
            <p class="app-muted">Confirming your payment…</p>
          }
          @case ('success') {
            <p class="text-green-600">
              Payment received. Transaction <strong>{{ trxId() }}</strong>.
            </p>
            <a [routerLink]="orderLink().path" [queryParams]="orderLink().query" class="btn-primary inline-block">View order</a>
          }
          @case ('failed') {
            <p class="text-red-500">{{ errorMessage() || 'Payment did not complete.' }}</p>
            <a [routerLink]="orderLink().path" [queryParams]="orderLink().query" class="btn-secondary inline-block">View order</a>
          }
          @case ('cancelled') {
            <p class="text-amber-600">Payment was cancelled.</p>
            <a routerLink="/cart" class="btn-secondary inline-block">Back to cart</a>
          }
          @default {
            <p class="text-red-500">Missing payment information in the callback URL.</p>
            <a routerLink="/" class="btn-secondary inline-block">Home</a>
          }
        }
      </article>
    </section>
  `,
})
export class BkashCallbackPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api   = inject(ApiService);
  private readonly router = inject(Router);
  private readonly pixel = inject(PixelService);

  protected readonly state        = signal<State>('verifying');
  protected readonly orderId      = signal<string | null>(null);
  protected readonly trxId        = signal<string | null>(null);
  protected readonly errorMessage = signal<string>('');

  /** The order number when this payment belongs to a guest order placed in this browser tab. */
  private guestOrder(orderId: string): string | null {
    try {
      const saved = JSON.parse(sessionStorage.getItem('erezer-guest-order') ?? 'null');
      return saved?.id === orderId ? saved.number : null;
    } catch {
      return null;
    }
  }

  /** Where "View order" goes: the order page, or tracking for a guest order. */
  protected orderLink(): { path: string[]; query: Record<string, string> } {
    const oid = this.orderId() ?? '';
    const guest = oid ? this.guestOrder(oid) : null;
    return guest ? { path: ['/track-order'], query: { number: guest } } : { path: ['/orders', oid], query: {} };
  }

  ngOnInit(): void {
    const qp = this.route.snapshot.queryParamMap;
    const paymentId = qp.get('paymentId');
    const status    = qp.get('status');
    this.orderId.set(qp.get('orderId'));

    if (!paymentId) {
      this.state.set('invalid');
      return;
    }
    if (status === 'cancelled' || status === 'cancel') {
      this.state.set('cancelled');
      return;
    }

    this.api.bkashExecute(paymentId)
      .pipe(catchError((err) => {
        this.errorMessage.set(err?.error?.message ?? 'Failed to confirm payment.');
        return of({ status: 'Failed' } as BkashPaymentResponse);
      }))
      .subscribe((response) => {
        if (response.status === 'Completed') {
          this.trxId.set(response.trxId);
          this.state.set('success');
          const oid = this.orderId();
          // Money has moved: report the purchase parked by the checkout page.
          if (oid) this.pixel.purchaseDeferred(oid, response.amount);
          if (oid) {
            const guest = this.guestOrder(oid);
            setTimeout(() => void (guest
              ? this.router.navigate(['/order-placed'], { queryParams: { number: guest } })
              : this.router.navigateByUrl(`/orders/${oid}`)), 1500);
          }
        } else {
          this.errorMessage.set(response.errorMessage ?? 'Payment was not completed.');
          this.state.set('failed');
        }
      });
  }
}
