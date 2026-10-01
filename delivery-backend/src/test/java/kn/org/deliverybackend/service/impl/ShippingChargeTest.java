package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.shipping.BasketShipping;
import kn.org.deliverybackend.dto.shipping.ShippingQuote;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.ShippingZone;
import kn.org.deliverybackend.entity.StoreSettings;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Delivery charges set on a product or on its category (V21).
 *
 * <p>A product's own charge wins; failing that its category's; failing that the
 * customer's area price. An order pays the highest charge in its basket, once.
 */
class ShippingChargeTest {

    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ShippingCharges charges = new ShippingCharges(categories);

    private final ShippingZoneRepository zones = mock(ShippingZoneRepository.class);
    private final StoreSettingsRepository settingsRepo = mock(StoreSettingsRepository.class);
    private final ShippingServiceImpl shipping = new ShippingServiceImpl(
            zones, mock(TaxRuleRepository.class), settingsRepo,
            mock(ProductRepository.class), categories);

    private final StoreSettings settings = new StoreSettings();
    private ShippingZone insideDhaka;

    @BeforeEach
    void setUp() {
        settings.setId(StoreSettings.SINGLETON_ID);
        when(settingsRepo.findById(StoreSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        insideDhaka = new ShippingZone();
        insideDhaka.setId(1L);
        insideDhaka.setDisplayName("Inside Dhaka");
        insideDhaka.setFlatFee(tk("60"));
    }

    private static BigDecimal tk(String v) {
        return new BigDecimal(v);
    }

    /** A product with a charge of its own, in a category that may have one too. */
    private Product product(String ownCharge, Long categoryId) {
        Product p = new Product();
        p.setId((long) (Math.random() * 1_000_000));
        p.setCategoryId(categoryId);
        if (ownCharge != null) p.setShippingCharge(tk(ownCharge));
        return p;
    }

    private void categoryCharges(long id, String charge) {
        Category c = new Category();
        c.setId(id);
        c.setName("Saree");
        if (charge != null) c.setShippingCharge(tk(charge));
        when(categories.findById(id)).thenReturn(Optional.of(c));
    }

    // ── which charge applies to one line ───────────────────────────────────────

    @Test
    void aProductsOwnChargeWins() {
        categoryCharges(7L, "150");
        BasketShipping basket = charges.forBasket(List.of(product("40", 7L)));
        assertEquals(0, basket.chargeAgainst(tk("60")).compareTo(tk("40")),
                "the product says 40, so the category's 150 and the area's 60 are both ignored");
    }

    @Test
    void theCategorysChargeAppliesWhenTheProductHasNone() {
        categoryCharges(7L, "150");
        BasketShipping basket = charges.forBasket(List.of(product(null, 7L)));
        assertEquals(0, basket.chargeAgainst(tk("60")).compareTo(tk("150")));
    }

    @Test
    void theAreaPriceAppliesWhenNeitherSetsOne() {
        categoryCharges(7L, null);
        BasketShipping basket = charges.forBasket(List.of(product(null, 7L)));
        assertNull(basket.highestCharge(), "nothing set is not the same as zero");
        assertEquals(0, basket.chargeAgainst(tk("60")).compareTo(tk("60")));
        assertEquals(0, basket.chargeAgainst(tk("120")).compareTo(tk("120")),
                "and it follows the area, so outside Dhaka costs more");
    }

    @Test
    void zeroMeansFreeAndIsNotMistakenForUnset() {
        BasketShipping basket = charges.forBasket(List.of(product("0", null)));
        assertTrue(basket.decidedByItems(), "0 is an answer: this product is delivered free");
        assertEquals(0, basket.chargeAgainst(tk("60")).compareTo(BigDecimal.ZERO));
    }

    @Test
    void aCategoryIsLookedUpOnceHoweverManyLinesShareIt() {
        categoryCharges(7L, "150");
        charges.forBasket(List.of(product(null, 7L), product(null, 7L), product(null, 7L)));
        verify(categories, times(1)).findById(7L);
    }

    // ── what a whole basket costs ──────────────────────────────────────────────

    @Test
    void theHighestChargeInTheBasketWinsOnce() {
        BasketShipping basket = charges.forBasket(List.of(product("150", null), product("40", null)));
        assertEquals(0, basket.chargeAgainst(tk("60")).compareTo(tk("150")),
                "one delivery, one charge: 150 — not 190");
    }

    @Test
    void aLineWithNoChargeOfItsOwnStillPaysTheAreaPrice() {
        categoryCharges(7L, null);
        // A ৳40 saree and a plain t-shirt, inside Dhaka where delivery is ৳60.
        BasketShipping basket = charges.forBasket(List.of(product("40", null), product(null, 7L)));
        assertEquals(0, basket.chargeAgainst(tk("60")).compareTo(tk("60")),
                "the t-shirt falls back to the area's 60, which is the highest of the two");
    }

    @Test
    void aFreeProductBesideANormalOneDoesNotMakeTheOrderFree() {
        categoryCharges(7L, null);
        BasketShipping basket = charges.forBasket(List.of(product("0", null), product(null, 7L)));
        assertEquals(0, basket.chargeAgainst(tk("60")).compareTo(tk("60")));
    }

    @Test
    void anEmptyBasketJustUsesTheAreaPrice() {
        assertEquals(0, BasketShipping.areaPriceOnly().chargeAgainst(tk("120")).compareTo(tk("120")));
        assertEquals(0, charges.forBasket(List.of()).chargeAgainst(tk("120")).compareTo(tk("120")));
    }

    // ── the shop's own rules still come first ──────────────────────────────────

    @Test
    void chargedByDefaultFromWhatTheBasketSays() {
        ShippingQuote q = shipping.quoteShipping(insideDhaka, tk("1800"),
                new BasketShipping(tk("150"), false));
        assertEquals(0, q.fee().compareTo(tk("150")));
        assertNull(q.freeReason());
    }

    @Test
    void freeShippingForAllOrdersBeatsAProductsCharge() {
        settings.setShippingFreeAll(true);
        ShippingQuote q = shipping.quoteShipping(insideDhaka, tk("1800"),
                new BasketShipping(tk("150"), false));
        assertEquals(0, q.fee().compareTo(BigDecimal.ZERO));
        assertEquals(ShippingQuote.FREE_ALL, q.freeReason());
    }

    @Test
    void theFreeShippingOfferBeatsAProductsCharge() {
        settings.setShippingOfferEnabled(true);
        settings.setShippingOfferMin(tk("2000"));
        ShippingQuote q = shipping.quoteShipping(insideDhaka, tk("2400"),
                new BasketShipping(tk("150"), false));
        assertEquals(0, q.fee().compareTo(BigDecimal.ZERO));
        assertEquals(ShippingQuote.OFFER, q.freeReason());
    }

    @Test
    void anOrderUnderTheOfferStillPaysTheProductsCharge() {
        settings.setShippingOfferEnabled(true);
        settings.setShippingOfferMin(tk("2000"));
        ShippingQuote q = shipping.quoteShipping(insideDhaka, tk("1900"),
                new BasketShipping(tk("150"), false));
        assertEquals(0, q.fee().compareTo(tk("150")));
        assertEquals(0, q.offerMin().compareTo(tk("2000")), "and the offer is still advertised");
    }

    @Test
    void aBasketSetToBeDeliveredFreeSaysWhyItIsFree() {
        ShippingQuote q = shipping.quoteShipping(insideDhaka, tk("300"),
                new BasketShipping(BigDecimal.ZERO, false));
        assertEquals(0, q.fee().compareTo(BigDecimal.ZERO));
        assertEquals(ShippingQuote.PRODUCT, q.freeReason());
    }

    @Test
    void aFreeShippingCouponStillWaivesACharge() {
        ShippingQuote q = shipping.quoteShipping(insideDhaka, tk("300"),
                new BasketShipping(tk("150"), false)).waivedByCoupon();
        assertEquals(0, q.fee().compareTo(BigDecimal.ZERO));
        assertEquals(ShippingQuote.COUPON, q.freeReason());
    }

    @Test
    void nothingSetAnywhereLeavesTheOldBehaviourExactlyAsItWas() {
        ShippingQuote withBasket = shipping.quoteShipping(insideDhaka, tk("300"),
                BasketShipping.areaPriceOnly());
        ShippingQuote withoutBasket = shipping.quoteShipping(insideDhaka, tk("300"));
        assertEquals(0, withBasket.fee().compareTo(tk("60")));
        assertEquals(0, withoutBasket.fee().compareTo(tk("60")));
    }
}
