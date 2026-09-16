package kn.org.deliverybackend.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Customer emails show taka, not dollars. */
class TakaTest {

    @Test
    void amountsCarryTheTakaSignWithGroupingAndPaisa() {
        assertEquals("\u09F31,260.00", Taka.format(new BigDecimal("1260")));
        assertEquals("\u09F3120,500.50", Taka.format(new BigDecimal("120500.5")));
        assertEquals("\u09F30.00", Taka.format(null));
    }
}
