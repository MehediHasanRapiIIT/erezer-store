package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.AddressesDTO;
import kn.org.deliverybackend.entity.Addresses;
import kn.org.deliverybackend.entity.Coupon;
import kn.org.deliverybackend.entity.Users;
import kn.org.deliverybackend.exception.InvalidStockOperationException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.mapper.AddressesMapper;
import kn.org.deliverybackend.repository.AddressesRepository;
import kn.org.deliverybackend.repository.CouponRedemptionRepository;
import kn.org.deliverybackend.repository.CouponRepository;
import kn.org.deliverybackend.repository.StoreSettingsRepository;
import kn.org.deliverybackend.repository.UsersRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The fixes from the code review: addresses belong to their owner; coupons keep all their rules. */
class SafetyFixesTest {

    // ── addresses ─────────────────────────────────────────────────────────────

    private final AddressesRepository addresses = mock(AddressesRepository.class);
    private final UsersRepository users = mock(UsersRepository.class);
    private final AddressServiceImpl addressService =
            new AddressServiceImpl(addresses, users, mock(AddressesMapper.class));

    @Test
    void aCustomerCannotChangeOrDeleteAnotherCustomersAddress() {
        UUID me = UUID.randomUUID(), someoneElse = UUID.randomUUID(), theirAddress = UUID.randomUUID();
        when(users.findById(me)).thenReturn(Optional.of(new Users()));
        Addresses theirs = new Addresses();
        theirs.setConsumerId(someoneElse);
        // The old lookup by id alone would have found it…
        when(addresses.findById(theirAddress)).thenReturn(Optional.of(theirs));
        // …the owner's lookup doesn't.
        when(addresses.findByIdAndConsumerId(theirAddress, me)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> addressService.updateAddress(me, theirAddress, new AddressesDTO()));
        assertThrows(ResourceNotFoundException.class, () -> addressService.deleteAddress(me, theirAddress));
        verify(addresses, never()).save(any());
    }

    // ── coupons ───────────────────────────────────────────────────────────────

    private final CouponRepository coupons = mock(CouponRepository.class);
    private final CouponRedemptionRepository redemptions = mock(CouponRedemptionRepository.class);
    private final CouponServiceImpl couponService =
            new CouponServiceImpl(coupons, redemptions, mock(StoreSettingsRepository.class));

    private Coupon coupon(BigDecimal minOrder, Integer perUser) {
        Coupon c = new Coupon();
        c.setId(UUID.randomUUID());
        c.setCode("BIG500");
        c.setDiscountType("FIXED");
        c.setDiscountValue(new BigDecimal("500"));
        c.setIsActive(true);
        c.setTimesUsed(0);
        c.setMinOrderAmount(minOrder);
        c.setPerUserLimit(perUser);
        when(coupons.findByCodeIgnoreCase("BIG500")).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void anOrderBelowTheMinimumSpendDoesNotGetTheCoupon() {
        coupon(new BigDecimal("2000"), null);
        InvalidStockOperationException e = assertThrows(InvalidStockOperationException.class,
                () -> couponService.getUsableForOrder("BIG500", new BigDecimal("500"), null));
        assertTrue(e.getMessage().contains("Minimum order"), e.getMessage());
        assertEquals("BIG500", couponService.getUsableForOrder("BIG500", new BigDecimal("2500"), null).getCode());
    }

    @Test
    void aCustomerWhoHasUsedTheirShareCannotUseItAgain() {
        Coupon c = coupon(null, 1);
        UUID customer = UUID.randomUUID();
        when(redemptions.countByCouponIdAndUserId(c.getId(), customer)).thenReturn(1L);
        assertThrows(InvalidStockOperationException.class,
                () -> couponService.getUsableForOrder("BIG500", new BigDecimal("3000"), customer));
        assertEquals("BIG500", couponService.getUsableForOrder("BIG500", new BigDecimal("3000"), UUID.randomUUID()).getCode(),
                "another customer still can");
    }
}
