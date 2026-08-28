package kn.org.deliverybackend.entity.enums;

public enum IssueType {
    ORDER_DELAYED,
    WRONG_ITEMS,
    FOOD_QUALITY,
    PAYMENT_FAILED,
    RIDER_RUDE,

    // Rider Issues
    CUSTOMER_UNREACHABLE,
    WRONG_ADDRESS,
    RESTAURANT_CLOSED,
    RESTAURANT_DELAY,

    // Restaurant Issues
    ORDER_DETAILS_WRONG,
    RIDER_NOT_COMING,
    PAYMENT_NOT_RECEIVED
}
