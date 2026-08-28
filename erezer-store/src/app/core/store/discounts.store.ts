import { inject, Injectable, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { ApiService } from '../api.service';
import { ApiActiveDiscount } from '../api.models';
import { effectiveUnitPrice } from '../discount-pricing';

/**
 * Loads the currently-active automatic discounts once and exposes a helper to
 * compute a product's effective unit price for display on cards/lists. The
 * authoritative price is always the server checkout quote; this is for display.
 */
@Injectable({ providedIn: 'root' })
export class DiscountsStore {
  private readonly api = inject(ApiService);

  readonly discounts = signal<ApiActiveDiscount[]>([]);

  constructor() {
    this.reload();
  }

  reload(): void {
    this.api.getActiveDiscounts()
      .pipe(catchError(() => of([] as ApiActiveDiscount[])))
      .subscribe((d) => this.discounts.set(d));
  }

  effectivePrice(basePrice: number, productId: number, categoryId: number): number {
    return effectiveUnitPrice(basePrice, productId, categoryId, this.discounts());
  }
}
