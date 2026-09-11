package kn.org.deliverybackend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How a product's sale price follows from its price and sale discount. Shared
 * by the product service, which stores it, and the product controller, which
 * uses it to tell whether an edit changes what customers pay.
 */
public final class ProductPricing {

    private ProductPricing() {}

    /** The price after the sale discount; the full price when there is no discount. */
    public static BigDecimal salePrice(BigDecimal price, BigDecimal discountPercentage) {
        if (price == null) return null;
        if (discountPercentage == null || discountPercentage.compareTo(BigDecimal.ZERO) <= 0) return price;
        BigDecimal discountAmount = price.multiply(discountPercentage)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return price.subtract(discountAmount);
    }

    /** Equal amounts, ignoring scale (150 and 150.00 are the same); two missing amounts are equal too. */
    public static boolean sameAmount(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a == b;
        return a.compareTo(b) == 0;
    }
}
