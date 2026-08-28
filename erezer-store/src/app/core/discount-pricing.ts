import { ApiActiveDiscount } from './api.models';

/**
 * Front-end mirror of the backend {@code DiscountEngine}. Computes the total
 * automatic discount for one line so product cards / the PDP can preview the
 * effective price. The authoritative number is always the server checkout quote;
 * this is for display only.
 *
 * Rules: gather applicable discounts (GLOBAL always; CATEGORY/PRODUCT by target),
 * sort by priority desc (tie-break: larger value, then id). The highest-priority
 * is the anchor; if it isn't stackable it applies alone, otherwise every other
 * stackable discount stacks on top. Applied sequentially against the reducing
 * balance, capped at the line subtotal.
 */
export function lineDiscount(
  productId: number,
  categoryId: number,
  lineSubtotal: number,
  discounts: ApiActiveDiscount[],
): number {
  if (!lineSubtotal || lineSubtotal <= 0 || !discounts?.length) return 0;

  const candidates = discounts.filter((d) => {
    if (d.scope === 'GLOBAL') return true;
    if (d.scope === 'CATEGORY') return d.targetId != null && d.targetId === categoryId;
    if (d.scope === 'PRODUCT') return d.targetId != null && d.targetId === productId;
    return false;
  });
  if (candidates.length === 0) return 0;

  candidates.sort((a, b) => {
    const pa = a.priority ?? 0;
    const pb = b.priority ?? 0;
    if (pa !== pb) return pb - pa;
    const va = a.discountValue ?? 0;
    const vb = b.discountValue ?? 0;
    if (va !== vb) return vb - va;
    return a.id.localeCompare(b.id);
  });

  const anchor = candidates[0];
  const applied = [anchor];
  if (anchor.stackable) {
    for (let i = 1; i < candidates.length; i++) {
      if (candidates[i].stackable) applied.push(candidates[i]);
    }
  }

  let remaining = lineSubtotal;
  for (const d of applied) {
    const value = d.discountValue ?? 0;
    const cut = d.discountType === 'PERCENT'
      ? Math.min((remaining * value) / 100, remaining)
      : Math.min(value, remaining);
    remaining -= cut;
    if (remaining <= 0) { remaining = 0; break; }
  }
  const discount = lineSubtotal - remaining;
  return Math.max(0, Math.min(discount, lineSubtotal));
}

/**
 * The base unit price before automatic discounts: the sale price when it's a
 * real discount (> 0), otherwise the regular price. Mirrors the backend
 * PricingSupport rule so cards and the product page agree.
 */
export function baseProductPrice(price: number, discountPrice: number | null | undefined): number {
  return discountPrice != null && discountPrice > 0 ? discountPrice : price;
}

/** Effective per-unit price after automatic discounts (display only). */
export function effectiveUnitPrice(
  basePrice: number,
  productId: number,
  categoryId: number,
  discounts: ApiActiveDiscount[],
): number {
  return basePrice - lineDiscount(productId, categoryId, basePrice, discounts);
}
