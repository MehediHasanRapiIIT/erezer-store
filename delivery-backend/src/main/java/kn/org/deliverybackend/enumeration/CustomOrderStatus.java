package kn.org.deliverybackend.enumeration;

/**
 * Lifecycle of a custom-design quote request. There is no price/checkout flow —
 * the admin negotiates with the customer over email after receiving the request.
 *
 *   NEW        → just submitted, unread by staff
 *   IN_REVIEW  → staff is preparing a quote / clarifying with the customer
 *   QUOTED     → a price was sent to the customer
 *   CONFIRMED  → customer accepted; production/fulfilment can proceed
 *   DELIVERED  → handed to the customer; moves to order history
 *   CLOSED     → completed or abandoned
 */
public enum CustomOrderStatus {
    NEW,
    IN_REVIEW,
    QUOTED,
    CONFIRMED,
    DELIVERED,
    CLOSED
}
