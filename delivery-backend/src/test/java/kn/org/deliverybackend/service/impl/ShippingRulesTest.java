package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.shipping.ShippingQuote;
import kn.org.deliverybackend.dto.shipping.ShippingRulesChangeDTO;
import kn.org.deliverybackend.entity.ShippingZone;
import kn.org.deliverybackend.entity.StoreSettings;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.ShippingZoneRepository;
import kn.org.deliverybackend.repository.StoreSettingsRepository;
import kn.org.deliverybackend.repository.TaxRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Shipping is charged unless an admin turns on free shipping for all, or the offer. */
class ShippingRulesTest {

    private final ShippingZoneRepository zones = mock(ShippingZoneRepository.class);
    private final StoreSettingsRepository settingsRepo = mock(StoreSettingsRepository.class);
    private final ShippingServiceImpl service =
            new ShippingServiceImpl(zones, mock(TaxRuleRepository.class), settingsRepo);

    private final StoreSettings settings = new StoreSettings();
    private ShippingZone insideDhaka;

    @BeforeEach
    void setUp() {
        settings.setId(StoreSettings.SINGLETON_ID);
        when(settingsRepo.findById(StoreSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        insideDhaka = new ShippingZone();
        insideDhaka.setId(1L);
        insideDhaka.setDisplayName("Inside Dhaka");
        insideDhaka.setFlatFee(new BigDecimal("60"));
        // An old seeded threshold must no longer give free shipping.
        insideDhaka.setFreeAbove(new BigDecimal("2000"));
        when(zones.findAllForAdmin()).thenReturn(List.of(insideDhaka));
    }

    private static BigDecimal tk(String v) {
        return new BigDecimal(v);
    }

    @Test
    void chargedByDefaultEvenForABigOrder() {
        ShippingQuote q = service.quoteShipping(insideDhaka, tk("6400"));
        assertEquals(0, q.fee().compareTo(tk("60")));
        assertNull(q.freeReason());
        assertNull(q.offerMin(), "no offer is advertised while it is off");
    }

    @Test
    void freeForAllOrders() {
        settings.setShippingFreeAll(true);
        ShippingQuote q = service.quoteShipping(insideDhaka, tk("100"));
        assertEquals(0, q.fee().signum());
        assertEquals(ShippingQuote.FREE_ALL, q.freeReason());
    }

    @Test
    void offerFreesOrdersFromTheMinimumOnly() {
        settings.setShippingOfferEnabled(true);
        settings.setShippingOfferMin(tk("2000"));

        ShippingQuote below = service.quoteShipping(insideDhaka, tk("1999.99"));
        assertEquals(0, below.fee().compareTo(tk("60")));
        assertEquals(0, below.offerMin().compareTo(tk("2000")), "checkout can still say the offer exists");

        ShippingQuote exactly = service.quoteShipping(insideDhaka, tk("2000"));
        assertEquals(0, exactly.fee().signum());
        assertEquals(ShippingQuote.OFFER, exactly.freeReason());
    }

    @Test
    void anOfferThatIsOffChangesNothing() {
        settings.setShippingOfferEnabled(false);
        settings.setShippingOfferMin(tk("2000"));
        assertEquals(0, service.quoteShipping(insideDhaka, tk("9000")).fee().compareTo(tk("60")));
    }

    @Test
    void aFreeShippingCouponWaivesTheFee() {
        ShippingQuote q = service.quoteShipping(insideDhaka, tk("500")).waivedByCoupon();
        assertEquals(0, q.fee().signum());
        assertEquals(ShippingQuote.COUPON, q.freeReason());
    }

    @Test
    void theOfferNeedsAnAmountBeforeItCanBeTurnedOn() {
        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> service.updateRules(new ShippingRulesChangeDTO(null, true, null)));
        assertTrue(ex.getMessage().contains("amount"), ex.getMessage());

        assertThrows(InvalidRequestException.class,
                () -> service.updateRules(new ShippingRulesChangeDTO(null, null, tk("0"))));
    }

    @Test
    void amountAndSwitchCanBeSetTogether() {
        var result = service.updateRules(new ShippingRulesChangeDTO(null, true, tk("2500")));
        assertTrue(result.offerEnabled());
        assertEquals(0, result.offerMin().compareTo(tk("2500")));
    }

    @Test
    void aNegativeZonePriceIsRefused() {
        when(zones.findById(1L)).thenReturn(Optional.of(insideDhaka));
        assertThrows(InvalidRequestException.class, () -> service.updateZoneFee(1L, tk("-1")));
    }
}
