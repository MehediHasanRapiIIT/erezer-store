package kn.org.deliverybackend.enumeration;

import java.util.Optional;

public enum CouponDiscountType {
    /** Percentage off the cart subtotal. {@code discountValue} is e.g. 10 for "10%". */
    PERCENT,
    /** Flat currency-amount off the cart subtotal. */
    FLAT,
    /** Removes the shipping fee. {@code discountValue} is ignored. */
    FREE_SHIPPING;

    public static Optional<CouponDiscountType> parse(String raw) {
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(CouponDiscountType.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
