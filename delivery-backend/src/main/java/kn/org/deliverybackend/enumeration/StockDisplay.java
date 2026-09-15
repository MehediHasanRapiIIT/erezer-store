package kn.org.deliverybackend.enumeration;

/** What a product's page shows about stock. */
public enum StockDisplay {
    /** Whatever the product's category says (the default). */
    CATEGORY,
    /** The real quantity: "12 in stock", "Only 3 left". */
    QUANTITY,
    /** Labels only: In stock, Only a few left, Out of stock. */
    LABEL;

    /**
     * Whether the product page shows the quantity. The product's own choice wins;
     * "follow the category" (or no choice) uses the category's switch, off by default.
     */
    public static boolean showsQuantity(StockDisplay productChoice, Boolean categoryShowsQuantity) {
        if (productChoice == QUANTITY) return true;
        if (productChoice == LABEL) return false;
        return Boolean.TRUE.equals(categoryShowsQuantity);
    }
}
