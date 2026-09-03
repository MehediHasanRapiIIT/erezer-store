import { inject, Injectable, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { ApiService } from '../api.service';
import { ApiActiveDiscount } from '../api.models';
import { effectiveUnitPrice, isDiscountExcluded } from '../discount-pricing';

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

  /**
   * Display price after automatic discounts.
   *
   * When the admin has switched discounts off, the backend returns no active
   * discounts at all, so this returns the base price without any special case.
   * `excluded` covers the narrower per-product and per-category exclusions.
   */
  effectivePrice(basePrice: number, productId: number, categoryId: number, excluded = false): number {
    return effectiveUnitPrice(basePrice, productId, categoryId, this.discounts(), excluded);
  }

  /** True when no automatic discount may touch this product. */
  isExcluded(product: { discountExcluded?: boolean | null; categoryDiscountExcluded?: boolean | null }): boolean {
    return isDiscountExcluded(product);
  }
}
