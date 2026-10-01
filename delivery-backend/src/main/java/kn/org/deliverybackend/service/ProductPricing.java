package kn.org.deliverybackend.service;

import kn.org.deliverybackend.exception.InvalidRequestException;

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

    /**
     * The price after a sale discount given either way: a percentage off, or a
     * fixed amount off in taka. Only the sale price is stored, so both are just
     * two ways of arriving at it.
     *
     * @throws InvalidRequestException when both are given, or the amount off
     *         would leave nothing to pay
     */
    public static BigDecimal salePrice(BigDecimal price, BigDecimal discountPercentage, BigDecimal discountAmount) {
        boolean byPercent = discountPercentage != null && discountPercentage.signum() > 0;
        boolean byAmount = discountAmount != null && discountAmount.signum() > 0;
        if (byPercent && byAmount) {
            throw new InvalidRequestException("Give the discount as a percentage or as an amount, not both.");
        }
        if (!byAmount) {
            return salePrice(price, discountPercentage);
        }
        if (price == null) return null;
        if (discountAmount.compareTo(price) >= 0) {
            throw new InvalidRequestException("The discount has to be less than the price.");
        }
        return price.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);
    }

    /** Equal amounts, ignoring scale (150 and 150.00 are the same); two missing amounts are equal too. */
    public static boolean sameAmount(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a == b;
        return a.compareTo(b) == 0;
    }
}
