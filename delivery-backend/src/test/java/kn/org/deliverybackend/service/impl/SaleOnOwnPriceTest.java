package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.Variant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A product's sale comes off a fit's (or a size's) own price too, the way the
 * shop owner gave it: the same percentage, or the same amount in taka.
 */
class SaleOnOwnPriceTest {

    private static Product product(String price, String salePrice, Boolean byAmount) {
        Product p = new Product();
        p.setPrice(new BigDecimal(price));
        p.setDiscountPrice(salePrice == null ? null : new BigDecimal(salePrice));
        p.setSaleByAmount(byAmount);
        return p;
    }

    private static Variant ownPrice(String price) {
        Variant v = new Variant();
        v.setPriceOverride(price == null ? null : new BigDecimal(price));
        return v;
    }

    private static void assertPays(String expected, Product p, Variant v) {
        assertEquals(0, new BigDecimal(expected).compareTo(PricingSupport.effectiveUnitPrice(p, v)),
                "pays " + PricingSupport.effectiveUnitPrice(p, v));
    }

    @Test
    void anAmountOffComesOffEachFitAsTheSameAmount() {
        Product tee = product("850", "750", true);          // 100 taka off
        assertPays("750", tee, ownPrice(null));              // Drop Shoulder, at the product's price
        assertPays("850", tee, ownPrice("950"));             // Regular Fit, 950 - 100
    }

    @Test
    void aPercentageOffComesOffEachFitAsTheSamePercentage() {
        Product tee = product("1000", "900", false);        // 10% off
        assertPays("900", tee, ownPrice(null));
        assertPays("1080", tee, ownPrice("1200"));           // 1200 - 10%
        assertPays("1080", product("1000", "900", null), ownPrice("1200")); // sales made before this was recorded
    }

    @Test
    void withNoSaleAFitSellsAtItsOwnPrice() {
        assertPays("950", product("850", null, null), ownPrice("950"));
        assertPays("950", product("850", "850", false), ownPrice("950"));
        assertPays("950", product("850", "0", false), ownPrice("950"));
    }

    @Test
    void anAmountOffThatWouldLeaveNothingIsNotTaken() {
        assertPays("80", product("850", "750", true), ownPrice("80"));
    }
}
