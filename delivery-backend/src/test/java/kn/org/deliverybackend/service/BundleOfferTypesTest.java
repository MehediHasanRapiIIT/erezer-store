package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.bundle.BundleOfferRequestDTO;
import kn.org.deliverybackend.dto.bundle.BundleOfferResponseDTO;
import kn.org.deliverybackend.dto.bundle.BundleTierDTO;
import kn.org.deliverybackend.dto.request.order.OrderItemRequestDTO;
import kn.org.deliverybackend.entity.BundleOffer;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.InvalidStockOperationException;
import kn.org.deliverybackend.repository.BundleOfferRepository;
import kn.org.deliverybackend.service.impl.BundlePricing;
import kn.org.deliverybackend.service.impl.BundleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The three kinds of bundle offer: what each asks the admin for, how many items
 * it takes, and what it takes off at checkout.
 */
class BundleOfferTypesTest {

    private final BundleOfferRepository repository = mock(BundleOfferRepository.class);
    private final ProductService productService = mock(ProductService.class);
    private final BundleServiceImpl service = new BundleServiceImpl(repository, productService);
    private final Map<UUID, BundleOffer> saved = new HashMap<>();

    @BeforeEach
    void setUp() {
        when(repository.save(any(BundleOffer.class))).thenAnswer(inv -> {
            BundleOffer b = inv.getArgument(0);
            if (b.getId() == null) b.setId(UUID.randomUUID());
            saved.put(b.getId(), b);
            return b;
        });
        when(repository.findById(any(UUID.class))).thenAnswer(inv -> Optional.ofNullable(saved.get(inv.<UUID>getArgument(0))));
        when(productService.getProductsByIds(any())).thenReturn(List.of());
    }

    // ── a fixed-price bundle ──────────────────────────────────────────────────

    @Test
    void aFixedPriceBundleIsASetNumberOfItemsForOnePrice() {
        BundleOfferResponseDTO b = service.create(request("FIXED_PRICE", 3, null, "999", null));

        assertEquals("FIXED_PRICE", b.getOfferType());
        assertEquals("Any 3 for ৳999", b.getHeadline());
        assertEquals(3, b.getMinItems());
        assertEquals(3, b.getMaxItems());
        assertEquals(0, b.getGetCount());

        // Three items worth 1,500 cost 999.
        assertMoney("501", service.bundleDiscount(b.getId(), items(3), new BigDecimal("1500")));
        // Never a negative discount when the items are already cheaper.
        assertMoney("0", service.bundleDiscount(b.getId(), items(3), new BigDecimal("900")));
        assertThrows(InvalidStockOperationException.class, () -> service.bundleDiscount(b.getId(), items(2), new BigDecimal("1000")));
        assertThrows(InvalidStockOperationException.class, () -> service.bundleDiscount(b.getId(), items(4), new BigDecimal("2000")));
    }

    @Test
    void differentQuantitiesCanEachHaveTheirOwnFixedPrice() {
        BundleOfferResponseDTO two = service.create(request("FIXED_PRICE", 2, null, "699", null));
        BundleOfferResponseDTO three = service.create(request("FIXED_PRICE", 3, null, "999", null));

        assertEquals("Any 2 for ৳699", two.getHeadline());
        assertMoney("301", service.bundleDiscount(two.getId(), items(2), new BigDecimal("1000")));
        assertMoney("501", service.bundleDiscount(three.getId(), items(3), new BigDecimal("1500")));
    }

    // ── buy X get Y ───────────────────────────────────────────────────────────

    @Test
    void buyXGetYIsPaidAndFreeItemsForOnePrice() {
        BundleOfferResponseDTO b = service.create(request("BUY_X_GET_Y", 2, 1, "1200", null));

        assertEquals("BUY_X_GET_Y", b.getOfferType());
        assertEquals("Buy 2 Get 1 Free", b.getHeadline());
        assertEquals(3, b.getSlots());
        assertMoney("600", service.bundleDiscount(b.getId(), items(3), new BigDecimal("1800")));
        assertThrows(InvalidStockOperationException.class, () -> service.bundleDiscount(b.getId(), items(2), new BigDecimal("1200")));
    }

    @Test
    void anOfferSavedWithoutAKindKeepsItsOldMeaning() {
        assertEquals("BUY_X_GET_Y", service.create(request(null, 1, 1, "500", null)).getOfferType());
        assertEquals("FIXED_PRICE", service.create(request(null, 3, 0, "999", null)).getOfferType());
    }

    // ── a quantity discount ───────────────────────────────────────────────────

    @Test
    void aQuantityDiscountTakesMoreOffTheMoreIsBought() {
        BundleOfferResponseDTO b = service.create(request("QUANTITY_DISCOUNT", null, null, null,
                List.of(tier(5, "20"), tier(2, "10"), tier(3, "15"))));

        assertEquals("QUANTITY_DISCOUNT", b.getOfferType());
        assertEquals("Buy 2, save 10% · Buy 3, save 15% · Buy 5, save 20%", b.getHeadline());
        assertEquals(List.of(2, 3, 5), b.getTiers().stream().map(BundleTierDTO::getQuantity).toList(), "steps are kept smallest first");
        assertEquals(2, b.getMinItems());
        assertEquals(BundlePricing.MAX_ITEMS, b.getMaxItems());
        assertNull(b.getSavings());

        assertMoney("100", service.bundleDiscount(b.getId(), items(2), new BigDecimal("1000")));
        assertMoney("225", service.bundleDiscount(b.getId(), items(3), new BigDecimal("1500")));
        assertMoney("300", service.bundleDiscount(b.getId(), items(4), new BigDecimal("2000")), "4 items are still on the 3-item step");
        assertMoney("500", service.bundleDiscount(b.getId(), items(5), new BigDecimal("2500")));
        assertMoney("1600", service.bundleDiscount(b.getId(), items(16), new BigDecimal("8000")), "past the last step, the last step's percentage");
        assertMoney("33.33", service.bundleDiscount(b.getId(), items(2), new BigDecimal("333.33")), "to the nearest paisa");
    }

    @Test
    void aQuantityDiscountNeedsTheSmallestStepAndStopsAtItsLimit() {
        BundleOfferResponseDTO b = service.create(request("QUANTITY_DISCOUNT", null, null, null, List.of(tier(3, "15"))));

        InvalidStockOperationException tooFew = assertThrows(InvalidStockOperationException.class,
                () -> service.bundleDiscount(b.getId(), items(2), new BigDecimal("1000")));
        assertTrue(tooFew.getMessage().contains("starts at 3"), tooFew.getMessage());
        assertThrows(InvalidStockOperationException.class,
                () -> service.bundleDiscount(b.getId(), items(BundlePricing.MAX_ITEMS + 1), new BigDecimal("9000")));
    }

    @Test
    void severalOfOneItemCountTowardsTheQuantity() {
        BundleOfferResponseDTO b = service.create(request("QUANTITY_DISCOUNT", null, null, null, List.of(tier(2, "10"), tier(4, "25"))));
        OrderItemRequestDTO four = new OrderItemRequestDTO();
        four.setProductId(1L);
        four.setQuantity(4);
        assertMoney("500", service.bundleDiscount(b.getId(), List.of(four), new BigDecimal("2000")));
    }

    // ── what the admin may save ───────────────────────────────────────────────

    @Test
    void eachKindSaysInPlainWordsWhatIsMissing() {
        assertMessage("how many items are in the bundle", () -> service.create(request("FIXED_PRICE", null, null, "999", null)));
        assertMessage("price of the bundle", () -> service.create(request("FIXED_PRICE", 3, null, null, null)));
        assertMessage("how many items are free", () -> service.create(request("BUY_X_GET_Y", 2, 0, "999", null)));
        assertMessage("at least one step", () -> service.create(request("QUANTITY_DISCOUNT", null, null, null, List.of())));
        assertMessage("quantity of 2 or more", () -> service.create(request("QUANTITY_DISCOUNT", null, null, null, List.of(tier(1, "10")))));
        assertMessage("above 0 and below 100", () -> service.create(request("QUANTITY_DISCOUNT", null, null, null, List.of(tier(2, "100")))));
        assertMessage("above 0 and below 100", () -> service.create(request("QUANTITY_DISCOUNT", null, null, null, List.of(tier(2, "0")))));
        assertMessage("Two steps are for 2 items", () -> service.create(request("QUANTITY_DISCOUNT", null, null, null, List.of(tier(2, "10"), tier(2, "12")))));
        assertMessage("must save more", () -> service.create(request("QUANTITY_DISCOUNT", null, null, null, List.of(tier(2, "15"), tier(3, "10")))));
    }

    @Test
    void changingAnOffersKindLeavesNothingOfTheOldKindBehind() {
        BundleOfferResponseDTO b = service.create(request("QUANTITY_DISCOUNT", null, null, null, List.of(tier(2, "10"))));
        BundleOfferResponseDTO fixed = service.update(b.getId(), request("FIXED_PRICE", 3, null, "999", null));

        assertEquals("FIXED_PRICE", fixed.getOfferType());
        assertTrue(fixed.getTiers().isEmpty());
        assertEquals(3, fixed.getMaxItems());
        assertMoney("501", service.bundleDiscount(b.getId(), items(3), new BigDecimal("1500")));
    }

    @Test
    void aProductOutsideTheOfferIsRefusedWhateverTheKind() {
        BundleOfferResponseDTO b = service.create(request("QUANTITY_DISCOUNT", null, null, null, List.of(tier(2, "10"))));
        List<OrderItemRequestDTO> items = items(2);
        items.get(1).setProductId(99L);
        assertThrows(InvalidStockOperationException.class, () -> service.bundleDiscount(b.getId(), items, new BigDecimal("1000")));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static BundleOfferRequestDTO request(String type, Integer buy, Integer get, String price, List<BundleTierDTO> tiers) {
        BundleOfferRequestDTO r = new BundleOfferRequestDTO();
        r.setName("Test offer");
        r.setOfferType(type);
        r.setBuyCount(buy);
        r.setGetCount(get);
        r.setBundlePrice(price == null ? null : new BigDecimal(price));
        r.setTiers(tiers == null ? new ArrayList<>() : new ArrayList<>(tiers));
        r.setProductIds(new ArrayList<>(List.of(1L, 2L, 3L)));
        return r;
    }

    private static BundleTierDTO tier(int quantity, String percent) {
        return new BundleTierDTO(quantity, new BigDecimal(percent));
    }

    /** That many items, one of each, cycling through the offer's three products. */
    private static List<OrderItemRequestDTO> items(int count) {
        List<OrderItemRequestDTO> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            OrderItemRequestDTO item = new OrderItemRequestDTO();
            item.setProductId((long) (i % 3) + 1);
            item.setQuantity(1);
            items.add(item);
        }
        return items;
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertMoney(expected, actual, null);
    }

    private static void assertMoney(String expected, BigDecimal actual, String message) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), (message == null ? "" : message + ": ") + "expected " + expected + " but was " + actual);
    }

    private static void assertMessage(String part, org.junit.jupiter.api.function.Executable action) {
        InvalidRequestException e = assertThrows(InvalidRequestException.class, action);
        assertTrue(e.getMessage().contains(part), e.getMessage());
    }
}
