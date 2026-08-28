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
 * <p>Precedence: variant price-override → product sale price ({@code discountPrice})
 * → product base price. A {@code discountPrice} of zero/negative is ignored (it
 * means "no sale", not "free").
 */
public final class PricingSupport {

    private PricingSupport() {
    }

    public static BigDecimal effectiveUnitPrice(Product product, Variant variant) {
        if (variant != null && variant.getPriceOverride() != null) {
            return variant.getPriceOverride();
        }
        return effectiveProductPrice(product);
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
