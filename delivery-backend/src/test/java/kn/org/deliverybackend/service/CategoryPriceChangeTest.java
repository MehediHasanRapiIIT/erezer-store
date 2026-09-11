package kn.org.deliverybackend.service;

import kn.org.deliverybackend.service.CategoryPriceChange.PriceMode;
import kn.org.deliverybackend.service.CategoryPriceChange.SaleMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static kn.org.deliverybackend.service.CategoryPriceChange.invalidReason;
import static kn.org.deliverybackend.service.CategoryPriceChange.newPrice;
import static kn.org.deliverybackend.service.CategoryPriceChange.newSalePrice;
import static kn.org.deliverybackend.service.CategoryPriceChange.newSizePrice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The price arithmetic of a category price change (CATEGORY-PRICE-PLAN.md). */
class CategoryPriceChangeTest {

    @Test
    void raisesAndLowersByAnAmount() {
        assertAmount("1500", newPrice(bd("1400"), PriceMode.RAISE_AMOUNT, bd("100")));
        assertAmount("1300", newPrice(bd("1400"), PriceMode.LOWER_AMOUNT, bd("100")));
    }

    @Test
    void percentagesRoundToWholeTaka() {
        // 1,299 less 15% is 1,104.15.
        assertAmount("1104", newPrice(bd("1299"), PriceMode.LOWER_PERCENT, bd("15")));
        // 1,005 plus 10% is 1,105.50, which rounds up.
        assertAmount("1106", newPrice(bd("1005"), PriceMode.RAISE_PERCENT, bd("10")));
    }

    @Test
    void anExactPriceIsRoundedToo() {
        assertAmount("1500", newPrice(bd("1400"), PriceMode.SET, bd("1499.60")));
    }

    @Test
    void keepingThePriceLeavesItExactlyAsItWas() {
        assertEquals(bd("1299.50"), newPrice(bd("1299.50"), PriceMode.KEEP, null));
    }

    @Test
    void anExactPriceKeepsEachSizesGap() {
        // The product goes from 1,400 to 1,500: XXL was 100 above and XS 50 below.
        assertAmount("1600", newSizePrice(bd("1500"), bd("1400"), bd("1500"), PriceMode.SET, bd("1500")));
        assertAmount("1450", newSizePrice(bd("1350"), bd("1400"), bd("1500"), PriceMode.SET, bd("1500")));
    }

    @Test
    void sizesGetTheSameAmountOrPercentage() {
        assertAmount("1600", newSizePrice(bd("1500"), bd("1400"), bd("1500"), PriceMode.RAISE_AMOUNT, bd("100")));
        assertAmount("1650", newSizePrice(bd("1500"), bd("1400"), bd("1540"), PriceMode.RAISE_PERCENT, bd("10")));
    }

    @Test
    void keepingTheSaleKeepsTheSameShareOff() {
        // 1,400 on sale at 1,260 is 10% off; at 1,540 that is 1,386.
        assertAmount("1386", newSalePrice(bd("1400"), bd("1260"), bd("1540"), SaleMode.KEEP, null));
        // The price itself kept: the sale price is left exactly as it was.
        assertEquals(bd("1259.99"), newSalePrice(bd("1400"), bd("1259.99"), bd("1400"), SaleMode.KEEP, null));
    }

    @Test
    void settingASaleWorksLikeTheProductForm() {
        assertAmount("1200", newSalePrice(bd("1400"), null, bd("1500"), SaleMode.SET, bd("20")));
        assertEquals(0, ProductPricing.salePrice(bd("1500"), bd("20"))
                .compareTo(newSalePrice(bd("1400"), bd("1300"), bd("1500"), SaleMode.SET, bd("20"))));
    }

    @Test
    void removingTheSaleStoresTheFullPriceLikeTheProductForm() {
        assertAmount("1500", newSalePrice(bd("1400"), bd("1260"), bd("1500"), SaleMode.REMOVE, null));
    }

    @Test
    void aProductNotOnSaleStaysOffSale() {
        assertNull(newSalePrice(bd("1400"), null, bd("1500"), SaleMode.KEEP, null));
        assertAmount("1500", newSalePrice(bd("1400"), bd("1400"), bd("1500"), SaleMode.KEEP, null));
    }

    @Test
    void refusesChangesThatMakeNoSense() {
        assertNotNull(invalidReason(PriceMode.KEEP, null, SaleMode.KEEP, null), "nothing to change");
        assertNotNull(invalidReason(PriceMode.SET, bd("0"), SaleMode.KEEP, null), "a zero price");
        assertNotNull(invalidReason(PriceMode.RAISE_AMOUNT, null, SaleMode.KEEP, null), "no amount");
        assertNotNull(invalidReason(PriceMode.LOWER_PERCENT, bd("100"), SaleMode.KEEP, null), "100% off the price");
        assertNotNull(invalidReason(PriceMode.KEEP, null, SaleMode.SET, bd("100")), "a 100% sale");
        assertNotNull(invalidReason(PriceMode.KEEP, null, SaleMode.SET, null), "a sale with no %");
        assertNull(invalidReason(PriceMode.RAISE_PERCENT, bd("10"), SaleMode.KEEP, null));
        assertNull(invalidReason(PriceMode.KEEP, null, SaleMode.SET, bd("25")));
        assertNull(invalidReason(PriceMode.KEEP, null, SaleMode.REMOVE, null));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static void assertAmount(String want, BigDecimal got) {
        assertEquals(0, bd(want).compareTo(got), () -> "expected " + want + ", got " + got);
    }
}
