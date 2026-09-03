package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Discount;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.StoreSettings;
import kn.org.deliverybackend.enumeration.DiscountScope;
import kn.org.deliverybackend.enumeration.DiscountType;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.DiscountRepository;
import kn.org.deliverybackend.repository.StoreSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * The admin's discount on/off switches decide what a customer is charged, so
 * each one is pinned here: the master switch, the three per-scope switches and
 * the per-product / per-category exclusions.
 *
 * <p>The two halves are tested where they live. Suspending a rule happens in
 * {@link DiscountServiceImpl#activeDiscounts()}; keeping one product or
 * category at full price happens in {@link DiscountEngine}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiscountSwitchTest {

    private static final long CATEGORY_ID = 4L;
    private static final long PRODUCT_ID = 31L;
    private static final BigDecimal LINE = new BigDecimal("1000.00");

    @Mock private DiscountRepository discountRepository;
    @Mock private StoreSettingsRepository storeSettingsRepository;
    @InjectMocks private DiscountServiceImpl discountService;

    @Mock private CategoryRepository categoryRepository;

    /** One rule of each scope, so a scope switch has something to suspend. */
    private List<Discount> allScopes;

    @BeforeEach
    void setUp() {
        allScopes = List.of(
                discount(DiscountScope.GLOBAL, null, 10),
                discount(DiscountScope.CATEGORY, CATEGORY_ID, 10),
                discount(DiscountScope.PRODUCT, PRODUCT_ID, 10));
        when(discountRepository.findActive()).thenReturn(allScopes);
        when(categoryRepository.findById(anyLong())).thenReturn(Optional.of(category(false)));
    }

    // ── master switch ───────────────────────────────────────────────────────

    @Test
    void masterSwitchOffSuspendsEveryDiscount() {
        settings(s -> s.setDiscountsEnabled(false));
        assertTrue(discountService.activeDiscounts().isEmpty());
    }

    @Test
    void masterSwitchOnLeavesEveryDiscountApplying() {
        settings(s -> s.setDiscountsEnabled(true));
        assertEquals(3, discountService.activeDiscounts().size());
    }

    @Test
    void nullSwitchesKeepDiscountingOnForShopsThatPredateTheFeature() {
        // Every flag null, as a row migrated from before the switches existed.
        settings(s -> { });
        assertEquals(3, discountService.activeDiscounts().size());
    }

    @Test
    void missingSettingsRowKeepsDiscountingOn() {
        when(storeSettingsRepository.findById(StoreSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        assertEquals(3, discountService.activeDiscounts().size());
    }

    // ── per-scope switches ──────────────────────────────────────────────────

    @Test
    void globalScopeOffSuspendsOnlyStoreWideRules() {
        settings(s -> s.setDiscountsGlobalEnabled(false));
        assertEquals(List.of("CATEGORY", "PRODUCT"), scopesOf(discountService.activeDiscounts()));
    }

    @Test
    void categoryScopeOffSuspendsOnlyCategoryRules() {
        settings(s -> s.setDiscountsCategoryEnabled(false));
        assertEquals(List.of("GLOBAL", "PRODUCT"), scopesOf(discountService.activeDiscounts()));
    }

    @Test
    void productScopeOffSuspendsOnlyProductRules() {
        settings(s -> s.setDiscountsProductEnabled(false));
        assertEquals(List.of("CATEGORY", "GLOBAL"), scopesOf(discountService.activeDiscounts()));
    }

    @Test
    void scopeSwitchesCombine() {
        settings(s -> {
            s.setDiscountsGlobalEnabled(false);
            s.setDiscountsCategoryEnabled(false);
        });
        assertEquals(List.of("PRODUCT"), scopesOf(discountService.activeDiscounts()));
    }

    @Test
    void masterSwitchBeatsAnIndividuallyEnabledScope() {
        settings(s -> {
            s.setDiscountsEnabled(false);
            s.setDiscountsGlobalEnabled(true);
            s.setDiscountsCategoryEnabled(true);
            s.setDiscountsProductEnabled(true);
        });
        assertTrue(discountService.activeDiscounts().isEmpty());
    }

    // ── per-product / per-category exclusions ───────────────────────────────

    @Test
    void excludedProductStaysAtFullPriceDespiteAStoreWideDiscount() {
        DiscountEngine engine = engineWith(discount(DiscountScope.GLOBAL, null, 10));
        Product product = product(true);
        assertEquals(0, BigDecimal.ZERO.compareTo(engine.discountForLine(product, LINE)));
    }

    @Test
    void excludedCategoryStaysAtFullPriceDespiteAStoreWideDiscount() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category(true)));
        DiscountEngine engine = engineWith(discount(DiscountScope.GLOBAL, null, 10));
        assertEquals(0, BigDecimal.ZERO.compareTo(engine.discountForLine(product(false), LINE)));
    }

    @Test
    void productThatIsNotExcludedStillGetsTheDiscount() {
        DiscountEngine engine = engineWith(discount(DiscountScope.GLOBAL, null, 10));
        // 10% of ৳1000.
        assertEquals(0, new BigDecimal("100.00").compareTo(engine.discountForLine(product(false), LINE)));
    }

    @Test
    void aProductWithNoCategoryIsPricedNormally() {
        DiscountEngine engine = engineWith(discount(DiscountScope.GLOBAL, null, 10));
        Product product = product(false);
        product.setCategoryId(null);
        assertEquals(0, new BigDecimal("100.00").compareTo(engine.discountForLine(product, LINE)));
    }

    @Test
    void aNullProductOrEmptyLineCostsNothing() {
        DiscountEngine engine = engineWith(discount(DiscountScope.GLOBAL, null, 10));
        assertEquals(0, BigDecimal.ZERO.compareTo(engine.discountForLine(null, LINE)));
        assertEquals(0, BigDecimal.ZERO.compareTo(engine.discountForLine(product(false), BigDecimal.ZERO)));
        assertEquals(0, BigDecimal.ZERO.compareTo(engine.discountForLine(product(false), null)));
    }

    /** The switch and the exclusion are independent, so both routes must end at full price. */
    @Test
    void masterSwitchOffAlsoReachesTheEngine() {
        settings(s -> s.setDiscountsEnabled(false));
        DiscountEngine engine = new DiscountEngine(discountService, categoryRepository);
        assertEquals(0, BigDecimal.ZERO.compareTo(engine.discountForLine(product(false), LINE)));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private DiscountEngine engineWith(Discount... discounts) {
        when(discountRepository.findActive()).thenReturn(List.of(discounts));
        settings(s -> { });
        return new DiscountEngine(discountService, categoryRepository);
    }

    private void settings(java.util.function.Consumer<StoreSettings> customise) {
        StoreSettings s = StoreSettings.builder().id(StoreSettings.SINGLETON_ID).build();
        customise.accept(s);
        when(storeSettingsRepository.findById(StoreSettings.SINGLETON_ID)).thenReturn(Optional.of(s));
    }

    private static List<String> scopesOf(List<Discount> discounts) {
        return discounts.stream().map(Discount::getScope).sorted().toList();
    }

    private static Discount discount(DiscountScope scope, Long targetId, int percent) {
        return Discount.builder()
                .id(UUID.nameUUIDFromBytes((scope.name() + targetId).getBytes()))
                .name(scope.name() + " rule")
                .scope(scope.name())
                .discountType(DiscountType.PERCENT.name())
                .discountValue(BigDecimal.valueOf(percent))
                .targetId(targetId)
                .stackable(false)
                .priority(0)
                .isActive(true)
                .build();
    }

    private static Product product(boolean excluded) {
        Product p = new Product();
        p.setId(PRODUCT_ID);
        p.setCategoryId(CATEGORY_ID);
        p.setDiscountExcluded(excluded);
        return p;
    }

    private static Category category(boolean excluded) {
        Category c = new Category();
        c.setId(CATEGORY_ID);
        c.setDiscountExcluded(excluded);
        return c;
    }
}
