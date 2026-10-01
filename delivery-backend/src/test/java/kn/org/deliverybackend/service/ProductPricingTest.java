package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.request.product.ProductRequestDTO;
import kn.org.deliverybackend.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A sale discount can be a percentage off or a fixed amount off. */
class ProductPricingTest {

    private static BigDecimal tk(String v) {
        return new BigDecimal(v);
    }

    private static void sameMoney(String want, BigDecimal got) {
        assertEquals(0, tk(want).compareTo(got), "wanted " + want + ", got " + got);
    }

    @Test
    void aPercentageOff() {
        sameMoney("850", ProductPricing.salePrice(tk("1000"), tk("15"), null));
    }

    @Test
    void aFixedAmountOff() {
        sameMoney("850", ProductPricing.salePrice(tk("1000"), null, tk("150")));
    }

    @Test
    void anAmountThatIsNotAWholePercentageIsKeptExactly() {
        // ৳999 with ৳150 off is 15.015% — a percentage could not say this exactly.
        sameMoney("849", ProductPricing.salePrice(tk("999"), null, tk("150")));
    }

    @Test
    void noDiscountIsTheFullPrice() {
        sameMoney("1000", ProductPricing.salePrice(tk("1000"), null, null));
        sameMoney("1000", ProductPricing.salePrice(tk("1000"), BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void bothAtOnceIsRefused() {
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> ProductPricing.salePrice(tk("1000"), tk("10"), tk("150")));
        assertTrue(e.getMessage().contains("not both"));
    }

    @Test
    void anAmountOffOfTheWholePriceOrMoreIsRefused() {
        assertThrows(InvalidRequestException.class,
                () -> ProductPricing.salePrice(tk("1000"), null, tk("1000")));
        assertThrows(InvalidRequestException.class,
                () -> ProductPricing.salePrice(tk("1000"), null, tk("1200")));
    }

    @Test
    void theRequestWorksOutItsOwnSalePrice() {
        ProductRequestDTO byAmount = new ProductRequestDTO();
        byAmount.setPrice(tk("1000"));
        byAmount.setDiscountAmount(tk("150"));
        sameMoney("850", byAmount.requestedSalePrice());

        ProductRequestDTO byPercent = new ProductRequestDTO();
        byPercent.setPrice(tk("1000"));
        byPercent.setDiscountPercentage(tk("15"));
        sameMoney("850", byPercent.requestedSalePrice());
    }
}
