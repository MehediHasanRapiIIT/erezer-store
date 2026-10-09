package kn.org.deliverybackend.enumeration;

/** The kinds of bundle offer. */
public enum BundleType {
    /** "Any 3 for 999": a set number of items for one price. */
    FIXED_PRICE,
    /** "Buy 2 Get 1": paid items plus free ones, for one price. */
    BUY_X_GET_Y,
    /** "Buy 2 save 10%, buy 3 save 15%": the more items, the bigger the percentage off. */
    QUANTITY_DISCOUNT;

    /** What an offer saved before types existed is: free items make it Buy X Get Y. */
    public static BundleType of(String stored, Integer getCount) {
        if (stored != null) {
            try {
                return valueOf(stored.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // An unknown value is read by its shape, below.
            }
        }
        return getCount != null && getCount > 0 ? BUY_X_GET_Y : FIXED_PRICE;
    }
}
