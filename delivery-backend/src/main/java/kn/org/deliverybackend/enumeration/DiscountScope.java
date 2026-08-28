package kn.org.deliverybackend.enumeration;

import java.util.Optional;

/** What a {@code Discount} applies to. */
public enum DiscountScope {
    /** A single product (targetId == productId). */
    PRODUCT,
    /** Every product in a category (targetId == categoryId). */
    CATEGORY,
    /** Store-wide — every product (targetId is null). */
    GLOBAL;

    public static Optional<DiscountScope> parse(String raw) {
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(DiscountScope.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
