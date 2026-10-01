package kn.org.deliverybackend.enumeration;

/** Which products a bulk change covers -- a stock change, or a delivery charge. */
public enum StockScope {
    /** Only the products chosen on the page. */
    PRODUCTS,
    /** Every product in one category. */
    CATEGORY,
    /** Every product in the shop. */
    ALL
}
