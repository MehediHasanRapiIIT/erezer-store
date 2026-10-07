package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.variant.ProductFitsRequestDTO;
import kn.org.deliverybackend.dto.variant.VariantRequestDTO;
import kn.org.deliverybackend.dto.variant.VariantResponseDTO;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.Variant;
import kn.org.deliverybackend.enumeration.Fit;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.VariantRepository;
import kn.org.deliverybackend.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fits: a product comes in Drop Shoulder, Regular Fit, both or neither, and each
 * fit has its own sizes, stock and price.
 */
class ProductFitsTest {

    private static final long PRODUCT = 7L;
    private static final String DS = Fit.DROP_SHOULDER.name(), RF = Fit.REGULAR_FIT.name();

    private final VariantRepository variants = mock(VariantRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final VariantServiceImpl service = new VariantServiceImpl(variants, products, inventory);

    /** Everything ever saved for the product; the live ones are what the repository returns. */
    private final List<Variant> stored = new ArrayList<>();
    private long nextId = 100;

    @BeforeEach
    void setUp() {
        Product p = new Product();
        p.setId(PRODUCT);
        p.setSku("HO-00007");
        when(products.findById(PRODUCT)).thenReturn(Optional.of(p));
        when(variants.findByProductId(PRODUCT)).thenAnswer(inv ->
                new ArrayList<>(stored.stream().filter(v -> !Boolean.TRUE.equals(v.getDeleted())).toList()));
        when(variants.findByProductIdAndSku(anyLong(), anyString())).thenAnswer(inv ->
                stored.stream().filter(v -> inv.getArgument(1).equals(v.getSku())).findFirst());
        when(variants.save(any(Variant.class))).thenAnswer(inv -> keep(inv.getArgument(0)));
        when(variants.saveAndFlush(any(Variant.class))).thenAnswer(inv -> keep(inv.getArgument(0)));
        when(variants.saveAll(any())).thenAnswer(inv -> {
            List<Variant> all = new ArrayList<>();
            for (Variant v : inv.<Iterable<Variant>>getArgument(0)) all.add(keep(v));
            return all;
        });
    }

    private Variant keep(Variant v) {
        if (v.getId() == null) v.setId(nextId++);
        if (v.getDeleted() == null) v.setDeleted(false);
        if (!stored.contains(v)) stored.add(v);
        return v;
    }

    private static VariantRequestDTO size(String fit, String size, int stock) {
        VariantRequestDTO r = new VariantRequestDTO();
        r.setFit(fit);
        r.setSize(size);
        r.setStockQuantity(stock);
        return r;
    }

    private static ProductFitsRequestDTO fits(ProductFitsRequestDTO.Choice... choices) {
        return new ProductFitsRequestDTO(List.of(choices));
    }

    private static ProductFitsRequestDTO.Choice fit(String fit) {
        return new ProductFitsRequestDTO.Choice(fit, null, false);
    }

    private static ProductFitsRequestDTO.Choice fitAt(String fit, String price) {
        return new ProductFitsRequestDTO.Choice(fit, price == null ? null : new BigDecimal(price), true);
    }

    /** "DS S 5, DS M 3, RF S 0" for what the product has now. */
    private String now() {
        return service.listForProduct(PRODUCT).stream()
                .map(v -> (v.getFit() == null ? "" : (v.getFit().equals(DS) ? "DS " : "RF ")) + v.getSize() + " " + v.getStockQuantity())
                .reduce((a, b) -> a + ", " + b).orElse("");
    }

    // ── adding sizes ──────────────────────────────────────────────────────────

    @Test
    void theSameSizeCanBeInBothFitsEachWithItsOwnStock() {
        List<VariantResponseDTO> made = service.createAll(PRODUCT,
                List.of(size(DS, "S", 5), size(DS, "M", 8), size(RF, "S", 2), size(RF, "M", 4)));
        assertEquals("DS S 5, DS M 8, RF S 2, RF M 4", now());
        assertEquals("Drop Shoulder", made.get(0).getFitLabel());
        assertEquals("Drop Shoulder / S", made.get(0).getName());
        assertEquals("HO-00007-DS-S", made.get(0).getSku());
        assertEquals("HO-00007-RF-M", made.get(3).getSku());
        verify(inventory, atLeastOnce()).followSizes(PRODUCT);
    }

    @Test
    void aSizeTwiceInOneFitIsRefused() {
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> service.createAll(PRODUCT, List.of(size(DS, "S", 1), size(DS, "s", 1))));
        assertTrue(e.getMessage().startsWith("Drop Shoulder") && e.getMessage().contains("twice"), e.getMessage());
        assertTrue(stored.isEmpty());
    }

    @Test
    void sizesAllHaveAFitOrNoneDo() {
        assertThrows(InvalidRequestException.class,
                () -> service.createAll(PRODUCT, List.of(size(DS, "S", 1), size(null, "M", 1))));
        service.createAll(PRODUCT, List.of(size(DS, "S", 1)));
        assertThrows(InvalidRequestException.class, () -> service.create(PRODUCT, size(null, "M", 1)));
    }

    @Test
    void anUnknownFitIsRefused() {
        assertThrows(InvalidRequestException.class, () -> service.create(PRODUCT, size("SLIM", "M", 1)));
    }

    @Test
    void changingASizeKeepsItsFit() {
        VariantResponseDTO made = service.create(PRODUCT, size(RF, "M", 4));
        Variant row = stored.get(0);
        when(variants.findById(made.getId())).thenReturn(Optional.of(row));
        service.update(PRODUCT, made.getId(), size(null, "M", 9));
        assertEquals("RF M 9", now());
    }

    // ── changing a product's fits ─────────────────────────────────────────────

    @Test
    void plainSizesBecomeTheFirstFitAndKeepTheirStock() {
        service.createAll(PRODUCT, List.of(size(null, "S", 5), size(null, "M", 3)));
        service.setFits(PRODUCT, fits(fit(DS)));
        assertEquals("DS S 5, DS M 3", now());
    }

    @Test
    void aFitBeingAddedGetsEverySizeWithNoStock() {
        service.createAll(PRODUCT, List.of(size(DS, "S", 5), size(DS, "M", 3)));
        service.setFits(PRODUCT, fits(fit(DS), fit(RF)));
        assertEquals("DS S 5, DS M 3, RF S 0, RF M 0", now());
    }

    @Test
    void aPriceTypedForAFitGoesOnItsSizesOnly() {
        service.createAll(PRODUCT, List.of(size(null, "S", 5), size(null, "M", 3)));
        List<VariantResponseDTO> after = service.setFits(PRODUCT, fits(fit(DS), fitAt(RF, "650")));
        for (VariantResponseDTO v : after) {
            if (RF.equals(v.getFit())) assertEquals(0, new BigDecimal("650").compareTo(v.getPriceOverride()));
            else assertNull(v.getPriceOverride(), "Drop Shoulder keeps the product's price");
        }
        // Empty again means "the product's price": the fit's own price is taken off.
        after = service.setFits(PRODUCT, fits(fit(DS), fitAt(RF, null)));
        assertTrue(after.stream().allMatch(v -> v.getPriceOverride() == null));
    }

    @Test
    void aFitTakenAwayGoesWithItsStock() {
        service.createAll(PRODUCT, List.of(size(DS, "S", 5), size(RF, "S", 2)));
        service.setFits(PRODUCT, fits(fit(DS)));
        assertEquals("DS S 5", now());
    }

    @Test
    void aFitTakenAwayCanBeAddedAgainWithTheSameSkus() {
        service.createAll(PRODUCT, List.of(size(DS, "S", 5), size(RF, "S", 2)));
        service.setFits(PRODUCT, fits(fit(DS)));
        List<VariantResponseDTO> again = service.setFits(PRODUCT, fits(fit(DS), fit(RF)));
        assertEquals("DS S 5, RF S 0", now());
        assertEquals("HO-00007-RF-S", again.get(1).getSku(), "the removed size gave its SKU up");
    }

    @Test
    void aSizeDeletedCanBeAddedAgainWithTheSameSku() {
        VariantResponseDTO made = service.create(PRODUCT, size(null, "M", 4));
        when(variants.findById(made.getId())).thenReturn(Optional.of(stored.get(0)));
        service.delete(PRODUCT, made.getId());
        assertEquals("HO-00007-M", service.create(PRODUCT, size(null, "M", 1)).getSku());
    }

    @Test
    void swappingToTheOtherFitStillLeavesEverySize() {
        service.createAll(PRODUCT, List.of(size(RF, "S", 5), size(RF, "M", 2)));
        service.setFits(PRODUCT, fits(fit(DS)));
        assertEquals("DS S 0, DS M 0", now());
    }

    @Test
    void noFitsFoldsBothBackIntoPlainSizesAddingTheirStock() {
        service.createAll(PRODUCT, List.of(size(DS, "S", 5), size(DS, "M", 3), size(RF, "S", 2), size(RF, "M", 4)));
        service.setFits(PRODUCT, fits());
        assertEquals("S 7, M 7", now());
    }

    @Test
    void aProductWithNoSizesCannotComeInFits() {
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> service.setFits(PRODUCT, fits(fit(DS))));
        assertTrue(e.getMessage().contains("sizes first"), e.getMessage());
    }

    @Test
    void theSameFitTwiceIsRefused() {
        service.createAll(PRODUCT, List.of(size(null, "S", 5)));
        assertThrows(InvalidRequestException.class, () -> service.setFits(PRODUCT, fits(fit(DS), fit(DS))));
    }

    // ── how a fit reads ───────────────────────────────────────────────────────

    @Test
    void whatTheCustomerChoseReadsFitThenSize() {
        assertEquals("Drop Shoulder / M", Fit.describe(DS, "M"));
        assertEquals("M", Fit.describe(null, "M"));
        assertEquals(Fit.REGULAR_FIT, Fit.parse("regular fit"));
        assertNull(Fit.parse("  "));
    }
}
