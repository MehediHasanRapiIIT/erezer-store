package kn.org.deliverybackend.dto.coupon;

/**
 * The promo code on/off switch, kept apart from the rest of store settings so
 * it can be given to staff on its own (like the discount switches).
 */
public record CouponSwitchDTO(Boolean couponsEnabled) {
}
