/**
 * The sale discount, as a percentage, that turns `price` into the stored
 * `discountPrice`. Sending it back when re-saving a product keeps its sale
 * price; leaving it out removes the sale.
 *
 * Six decimals, because the server rounds the sale price to the paisa: 849
 * from 999 needs 15.015015%, while a rounded 15.02% would give 848.95.
 * Undefined when the product has no sale price.
 */
export function salePercent(price: number, discountPrice: number | null | undefined): number | undefined {
  if (!(price > 0) || discountPrice == null || discountPrice >= price) return undefined;
  return Math.round(((price - discountPrice) / price) * 100 * 1e6) / 1e6;
}
