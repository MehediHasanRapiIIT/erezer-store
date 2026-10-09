package kn.org.deliverybackend.enumeration;

/** What "do this to the ticked products" on the Products page can do. */
public enum BulkProductAction {
    /** Put the products in another category. Their codes stay as they are. */
    MOVE_CATEGORY,
    DELETE,
    /** On sale in the shop, or hidden from it. */
    SHOW,
    HIDE,
    /** "Featured products" on the home page. */
    FEATURE,
    UNFEATURE,
    /** "New arrivals" on the home page. */
    NEW_ARRIVAL_ON,
    NEW_ARRIVAL_OFF,
    /** Kept out of automatic discounts, or let back in. */
    NEVER_DISCOUNT_ON,
    NEVER_DISCOUNT_OFF,
    /** The product page shows how many are left, or only labels like "In stock". */
    STOCK_SHOW_QUANTITY,
    STOCK_SHOW_LABELS,
    /** Give them one size chart from the library, or (with no chart) let them follow their category again. */
    SET_SIZE_CHART
}
