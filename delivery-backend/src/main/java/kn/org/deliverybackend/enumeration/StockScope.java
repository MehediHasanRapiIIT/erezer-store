package kn.org.deliverybackend.enumeration;

/** Which products a bulk stock change covers. */
public enum StockScope {
    /** Only the products chosen on the Inventory page. */
    PRODUCTS,
    /** Every product in one category. */
    CATEGORY,
    /** Every product in the shop. */
    ALL
}
