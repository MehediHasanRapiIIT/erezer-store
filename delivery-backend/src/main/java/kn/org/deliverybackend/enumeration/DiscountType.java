package kn.org.deliverybackend.enumeration;

import java.util.Optional;

/** How a {@code Discount} reduces a line's price. */
public enum DiscountType {
    /** Percentage off the line subtotal. {@code discountValue} is e.g. 10 for "10%". */
    PERCENT,
    /** Flat currency-amount off the line subtotal. */
    FLAT;

    public static Optional<DiscountType> parse(String raw) {
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(DiscountType.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
