package kn.org.deliverybackend.enumeration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Quantity or labels on the product page: the product's own choice wins, otherwise its category. */
class StockDisplayTest {

    @Test
    void followingTheCategoryUsesItsSwitchOffByDefault() {
        assertFalse(StockDisplay.showsQuantity(StockDisplay.CATEGORY, null));
        assertFalse(StockDisplay.showsQuantity(StockDisplay.CATEGORY, false));
        assertTrue(StockDisplay.showsQuantity(StockDisplay.CATEGORY, true));
        assertTrue(StockDisplay.showsQuantity(null, true), "no choice saved yet = follow the category");
        assertFalse(StockDisplay.showsQuantity(null, null));
    }

    @Test
    void aProductCanShowTheQuantityInACategoryThatDoesNot() {
        assertTrue(StockDisplay.showsQuantity(StockDisplay.QUANTITY, false));
        assertTrue(StockDisplay.showsQuantity(StockDisplay.QUANTITY, null));
    }

    @Test
    void aProductCanKeepLabelsInACategoryThatShowsQuantities() {
        assertFalse(StockDisplay.showsQuantity(StockDisplay.LABEL, true));
    }
}
