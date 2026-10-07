package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.Variant;

import java.math.BigDecimal;

/**
 * The single source of truth for a line's <em>effective unit price</em> before
 * automatic discounts and coupons. Used identically by the checkout quote, order
 * placement, the order line-item snapshot, and the automatic-discount base so the
 * price a customer is shown is exactly the price they are charged.
 *
 * <p>A product with one price sells at its sale price ({@code discountPrice}) when
 * it has one, else at its base price. A fit or a size with a price of its own
 * starts from that price, and the product's sale comes off it too, the way the
 * shop owner gave it: the same percentage, or the same amount in taka. A
 * {@code discountPrice} of zero/negative is ignored (it means "no sale", not
 * "free").
 */
public final class PricingSupport {

    private PricingSupport() {
    }

    public static BigDecimal effectiveUnitPrice(Product product, Variant variant) {
        if (variant != null && variant.getPriceOverride() != null) {
            return saleOn(product, variant.getPriceOverride());
        }
        return effectiveProductPrice(product);
    }

    /**
     * A fit's or a size's own price after the product's sale. "10% off" takes 10%
     * off it; "100 taka off" takes 100 off it. No sale, or one that would leave
     * nothing to pay, leaves the own price as it is.
     */
    public static BigDecimal saleOn(Product product, BigDecimal ownPrice) {
        if (ownPrice == null || product == null) return ownPrice;
        BigDecimal price = product.getPrice();
        BigDecimal sale = product.getDiscountPrice();
        if (price == null || sale == null || price.signum() <= 0 || sale.signum() <= 0 || sale.compareTo(price) >= 0) {
            return ownPrice;
        }
        BigDecimal after = Boolean.TRUE.equals(product.getSaleByAmount())
                ? ownPrice.subtract(price.subtract(sale))
                : ownPrice.multiply(sale).divide(price, 2, java.math.RoundingMode.HALF_UP);
        return after.signum() > 0 ? after.setScale(2, java.math.RoundingMode.HALF_UP) : ownPrice;
    }

    /** Effective unit price for a product with no variant selected. */
    public static BigDecimal effectiveProductPrice(Product product) {
        BigDecimal sale = product.getDiscountPrice();
        if (sale != null && sale.signum() > 0) {
            return sale;
        }
        return product.getPrice();
    }
}
