package kn.org.deliverybackend.dto.shipping;

import java.math.BigDecimal;

/**
 * The shipping charge for one order after the shop's rules.
 *
 * @param fee        what the customer pays for shipping
 * @param freeReason why it is free: {@link #FREE_ALL}, {@link #OFFER}, {@link #COUPON}; null when charged
 * @param offerMin   the free-shipping offer's minimum while the offer is on, else null
 */
public record ShippingQuote(BigDecimal fee, String freeReason, BigDecimal offerMin) {

    public static final String FREE_ALL = "FREE_ALL";
    public static final String OFFER = "OFFER";
    public static final String COUPON = "COUPON";

    /** A free-shipping coupon waives the fee whatever the rules say. */
    public ShippingQuote waivedByCoupon() {
        return new ShippingQuote(BigDecimal.ZERO, COUPON, offerMin);
    }
}
