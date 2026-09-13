package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.coupon.CouponValidateRequestDTO;
import kn.org.deliverybackend.dto.coupon.CouponValidateResponseDTO;
import kn.org.deliverybackend.entity.Coupon;
import kn.org.deliverybackend.entity.StoreSettings;
import kn.org.deliverybackend.exception.InvalidStockOperationException;
import kn.org.deliverybackend.repository.CouponRedemptionRepository;
import kn.org.deliverybackend.repository.CouponRepository;
import kn.org.deliverybackend.repository.StoreSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The promo code master switch: off refuses every code, wherever it is used. */
class CouponSwitchTest {

    private final CouponRepository coupons = mock(CouponRepository.class);
    private final StoreSettingsRepository settings = mock(StoreSettingsRepository.class);
    private final CouponServiceImpl service =
            new CouponServiceImpl(coupons, mock(CouponRedemptionRepository.class), settings);

    @BeforeEach
    void aValidTenPercentCode() {
        Coupon c = new Coupon();
        c.setCode("WELCOME10");
        c.setDiscountType("PERCENT");
        c.setDiscountValue(BigDecimal.TEN);
        c.setIsActive(true);
        c.setTimesUsed(0);
        when(coupons.findByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(c));
    }

    private void switchIs(Boolean on) {
        StoreSettings s = new StoreSettings();
        s.setId(StoreSettings.SINGLETON_ID);
        s.setCouponsEnabled(on);
        when(settings.findById(StoreSettings.SINGLETON_ID)).thenReturn(Optional.of(s));
    }

    private CouponValidateResponseDTO validate() {
        return service.validate(new CouponValidateRequestDTO("WELCOME10", new BigDecimal("1000"), null));
    }

    @Test
    void onByDefaultForAnOlderDatabase() {
        switchIs(null);
        assertTrue(validate().isValid());

        when(settings.findById(StoreSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        assertTrue(validate().isValid());
    }

    @Test
    void offRefusesTheCodeInTheCartWithoutLookingItUp() {
        switchIs(false);
        CouponValidateResponseDTO result = validate();

        assertFalse(result.isValid());
        assertEquals(CouponServiceImpl.CODES_OFF, result.getReason());
        verify(coupons, never()).findByCodeIgnoreCase(anyString());
    }

    @Test
    void offAlsoStopsACodeAppliedEarlierOrSentStraightToCheckout() {
        switchIs(false);
        InvalidStockOperationException ex = assertThrows(InvalidStockOperationException.class,
                () -> service.getActiveByCode("WELCOME10"));
        assertEquals(CouponServiceImpl.CODES_OFF, ex.getMessage());
    }

    @Test
    void backOnAndTheSameCouponWorksAgain() {
        switchIs(false);
        assertFalse(validate().isValid());

        switchIs(true);
        assertTrue(validate().isValid());
        assertEquals("WELCOME10", service.getActiveByCode("WELCOME10").getCode());
    }
}
