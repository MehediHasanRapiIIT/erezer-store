package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.entity.FitType;
import kn.org.deliverybackend.entity.SizeChart;
import kn.org.deliverybackend.enumeration.Fit;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.FitTypeRepository;
import kn.org.deliverybackend.repository.ProductFitSizeChartRepository;
import kn.org.deliverybackend.repository.SizeChartRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The shop's list of fits: adding Oversized with its chart, renaming, hiding,
 * ordering, removing one nobody uses, and the rest of the program seeing the
 * list through {@link Fit}.
 */
class FitCatalogTest {

    private static final long DS_CHART = 1, RF_CHART = 2, OVERSIZED_CHART = 3;

    private final FitTypeRepository fits = mock(FitTypeRepository.class);
    private final SizeChartRepository charts = mock(SizeChartRepository.class);
    private final ProductFitSizeChartRepository productCharts = mock(ProductFitSizeChartRepository.class);
    private final FitCatalog catalog = new FitCatalog(fits, charts, productCharts,
            mock(kn.org.deliverybackend.repository.CategoryFitSizeChartRepository.class),
            mock(kn.org.deliverybackend.repository.SizeFinderSettingRepository.class));
    private final List<FitType> stored = new ArrayList<>();
    private final Map<String, Long> productsIn = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        stored.add(fit(1L, "DROP_SHOULDER", "Drop Shoulder", "DS", DS_CHART, 1));
        stored.add(fit(2L, "REGULAR_FIT", "Regular Fit", "RF", RF_CHART, 2));
        productsIn.put("DROP_SHOULDER", 7L);
        when(fits.findAllInOrder()).thenAnswer(inv -> stored.stream()
                .sorted(Comparator.comparing(FitType::getSortOrder).thenComparing(FitType::getId)).toList());
        when(fits.findById(anyLong())).thenAnswer(inv -> stored.stream().filter(f -> f.getId().equals(inv.getArgument(0))).findFirst());
        when(fits.existsByCode(anyString())).thenAnswer(inv -> stored.stream().anyMatch(f -> f.getCode().equals(inv.getArgument(0))));
        when(fits.countProductsIn(anyString())).thenAnswer(inv -> productsIn.getOrDefault(inv.<String>getArgument(0), 0L));
        when(fits.save(any(FitType.class))).thenAnswer(inv -> {
            FitType f = inv.getArgument(0);
            if (f.getId() == null) {
                f.setId(stored.stream().mapToLong(FitType::getId).max().orElse(0) + 1);
                stored.add(f);
            }
            return f;
        });
        doAnswer(inv -> stored.remove(inv.<FitType>getArgument(0))).when(fits).delete(any(FitType.class));
        when(charts.findLive(anyLong())).thenAnswer(inv -> {
            long id = inv.getArgument(0);
            if (id > 3) return Optional.empty();
            SizeChart c = new SizeChart();
            c.setId(id);
            c.setName(id == DS_CHART ? "Drop Shoulder" : id == RF_CHART ? "Regular Fit" : "Oversized");
            return Optional.of(c);
        });
        catalog.refresh();
    }

    @AfterEach
    void putTheBuiltInFitsBack() {
        Fit.install(List.of(Fit.DROP_SHOULDER, Fit.REGULAR_FIT));
    }

    @Test
    void theListIsReadIntoTheRestOfTheProgramWithEachFitsChart() {
        assertEquals(List.of("DROP_SHOULDER", "REGULAR_FIT"), names());
        assertEquals(RF_CHART, Fit.valueOf("REGULAR_FIT").sizeChartId());
        assertEquals("Regular Fit / M", Fit.describe("REGULAR_FIT", "M"));
        List<FitCatalog.FitView> listed = catalog.list(true);
        assertEquals("Drop Shoulder", listed.get(0).sizeChartName());
        assertEquals(7, listed.get(0).productCount());
    }

    @Test
    void aNewFitGetsItsCodeSkuLettersChartAndPlaceAtTheEnd() {
        FitCatalog.FitView oversized = catalog.create("  Oversized ", OVERSIZED_CHART);

        assertEquals("OVERSIZED", oversized.code());
        assertEquals("OV", oversized.skuCode());
        assertEquals("Oversized", oversized.sizeChartName());
        assertEquals(3, oversized.position());
        assertEquals(List.of("DROP_SHOULDER", "REGULAR_FIT", "OVERSIZED"), names(), "the program sees it at once");
        assertEquals("Oversized", Fit.parse("Oversized").label(), "a form may name it by its name");
        assertEquals(OVERSIZED_CHART, Fit.valueOf("OVERSIZED").sizeChartId());
        assertEquals("LF", FitCatalog.skuCodeFor("Loose Fit"));
    }

    @Test
    void twoFitsCannotHaveTheSameNameAndAFitNeedsOne() {
        assertTrue(assertThrows(InvalidRequestException.class, () -> catalog.create("regular fit", null)).getMessage().contains("already"));
        assertThrows(InvalidRequestException.class, () -> catalog.create("   ", null));
        assertThrows(InvalidRequestException.class, () -> catalog.create("Boxy", 99L), "a chart that does not exist");
    }

    @Test
    void renamingKeepsTheCodeSoEveryProductKeepsItsSizes() {
        catalog.update(2L, "Classic Fit", null, null);
        assertEquals("REGULAR_FIT", stored.get(1).getCode());
        assertEquals("Classic Fit / L", Fit.describe("REGULAR_FIT", "L"));
        assertEquals(RF_CHART, stored.get(1).getSizeChartId(), "a missing chart leaves the chart as it is");
        catalog.update(2L, null, 0L, null);
        assertNull(stored.get(1).getSizeChartId(), "0 takes the chart away");
    }

    @Test
    void aFitInUseCannotBeRemovedOnlyHidden() {
        InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> catalog.delete(1L));
        assertTrue(e.getMessage().contains("used by 7 products") && e.getMessage().contains("Hide it"), e.getMessage());

        catalog.update(1L, null, null, true);
        assertTrue(Fit.valueOf("DROP_SHOULDER").hidden(), "hidden, but still known for the products that have it");
        assertFalse(catalog.list(false).stream().anyMatch(f -> f.code().equals("DROP_SHOULDER")), "and not offered for new products");
        assertTrue(assertThrows(InvalidRequestException.class, () -> catalog.update(2L, null, null, true)).getMessage().contains("At least one"));
    }

    @Test
    void aFitNobodyUsesIsRemovedWithTheProductsOwnChartsForIt() {
        catalog.create("Oversized", null);
        long id = stored.get(2).getId();
        catalog.delete(id);
        assertEquals(List.of("DROP_SHOULDER", "REGULAR_FIT"), names());
        org.mockito.Mockito.verify(productCharts).clearFit("OVERSIZED");
        assertThrows(IllegalArgumentException.class, () -> Fit.valueOf("OVERSIZED"));
    }

    @Test
    void theListCanBePutInAnyOrder() {
        catalog.create("Oversized", null);
        long oversized = stored.get(2).getId();
        List<FitCatalog.FitView> after = catalog.reorder(List.of(oversized, 1L));
        assertEquals(List.of("OVERSIZED", "DROP_SHOULDER", "REGULAR_FIT"), after.stream().map(FitCatalog.FitView::code).toList());
        assertEquals(List.of("OVERSIZED", "DROP_SHOULDER", "REGULAR_FIT"), names());
        assertEquals(0, Fit.rank("OVERSIZED"));
        assertThrows(InvalidRequestException.class, () -> catalog.reorder(List.of(999L)));
    }

    private static List<String> names() {
        return java.util.Arrays.stream(Fit.values()).map(Fit::name).toList();
    }

    private static FitType fit(Long id, String code, String name, String sku, Long chart, int order) {
        FitType f = new FitType();
        f.setId(id);
        f.setCode(code);
        f.setName(name);
        f.setSkuCode(sku);
        f.setSizeChartId(chart);
        f.setSortOrder(order);
        f.setHidden(false);
        return f;
    }
}
