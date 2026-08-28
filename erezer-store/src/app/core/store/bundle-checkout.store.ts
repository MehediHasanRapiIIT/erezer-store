import { computed, Injectable, signal } from '@angular/core';

/** One filled bundle slot. */
export interface BundleLine {
  productId: number;
  variantId: number | null;
  size: string;
  unitPrice: number;
  name: string;
  image: string | null;
}

export interface BundleCheckout {
  bundleId: string;
  bundleName: string;
  bundlePrice: number;
  lines: BundleLine[];
}

/**
 * Holds a pending "bundle checkout" so the customer can complete a Buy-X-Get-Y
 * bundle through the normal checkout page WITHOUT touching their real cart. When
 * a bundle is pending, the checkout page prices/submits these items (with the
 * bundleId) instead of the cart.
 */
@Injectable({ providedIn: 'root' })
export class BundleCheckoutStore {
  readonly pending = signal<BundleCheckout | null>(null);

  /** Items shaped like the cart's detailed items so checkout can render/price them. */
  readonly detailedItems = computed(() => {
    const bc = this.pending();
    if (!bc) return [];
    return bc.lines.map((l, i) => ({
      product: { id: l.productId, name: l.name, imageUrl: l.image },
      productId: l.productId,
      variantId: l.variantId,
      quantity: 1,
      size: l.size,
      unitPrice: l.unitPrice,
      subtotal: l.unitPrice,
      customMeasurements: null as string | null,
      // stable key for @for tracking (a product+size may repeat across slots)
      _key: `${l.productId}-${l.variantId ?? 'n'}-${i}`,
    }));
  });

  readonly count = computed(() => this.pending()?.lines.length ?? 0);

  start(bc: BundleCheckout): void {
    this.pending.set(bc);
  }

  clear(): void {
    this.pending.set(null);
  }
}
