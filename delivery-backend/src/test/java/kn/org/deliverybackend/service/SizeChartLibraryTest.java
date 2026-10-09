package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.settings.SizeChartCellDTO;
import kn.org.deliverybackend.dto.settings.SizeChartDTO;
import kn.org.deliverybackend.dto.settings.SizeChartLibraryDTO;
import kn.org.deliverybackend.dto.settings.SizeChartRowDTO;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.SizeChart;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.SizeChartRepository;
import kn.org.deliverybackend.service.impl.SizeChartLibraryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The size chart library: many charts, one default, and a product showing its
 * own chart, else its category's (or one from a category above), else the default.
 */
class SizeChartLibraryTest {

    private static final long GENERAL = 1, MENS_TEE = 2, KIDS = 3, REGULAR = 4;
    private static final long MEN = 10, TEES = 11, DROP = 12, CAPS = 20;

    private final SizeChartRepository charts = mock(SizeChartRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final SizeChartLibraryService service = new SizeChartLibraryService(charts, categories);
    private final Map<Long, SizeChart> chartsById = new HashMap<>();
    private final Map<Long, Category> categoriesById = new HashMap<>();
    private long nextId = 50;

    @BeforeEach
    void setUp() {
        chart(GENERAL, "General", true);
        chart(MENS_TEE, "Men's T-Shirt", false);
        chart(KIDS, "Kids", false);
        chart(REGULAR, "Regular Fit", false);
        category(MEN, null);
        category(TEES, MEN);
        category(DROP, TEES);
        category(CAPS, null);

        when(charts.findLive()).thenAnswer(inv -> live());
        when(charts.findLive(anyLong())).thenAnswer(inv -> Optional.ofNullable(chartsById.get(inv.<Long>getArgument(0)))
                .filter(c -> !Boolean.TRUE.equals(c.getDeleted())));
        when(charts.findDefaults()).thenAnswer(inv -> live().stream().filter(c -> Boolean.TRUE.equals(c.getIsDefault())).toList());
        when(charts.save(any(SizeChart.class))).thenAnswer(inv -> {
            SizeChart c = inv.getArgument(0);
            if (c.getId() == null) c.setId(nextId++);
            chartsById.put(c.getId(), c);
            return c;
        });
        when(categories.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(categoriesById.get(inv.<Long>getArgument(0))));
    }

    // ── which chart a product shows ───────────────────────────────────────────

    @Test
    void aProductWithNothingNamedShowsTheDefaultChart() {
        assertEquals(GENERAL, service.effectiveFor(product(CAPS, null, null)));
        assertEquals(GENERAL, service.effectiveFor(product(null, null, null)), "even with no category");
    }

    @Test
    void aCategorysChartCoversEverythingUnderItUntilSomethingNearerNamesItsOwn() {
        categoriesById.get(MEN).setSizeChartId(MENS_TEE);
        assertEquals(MENS_TEE, service.effectiveFor(product(MEN, null, null)));
        assertEquals(MENS_TEE, service.effectiveFor(product(DROP, null, null)), "two levels down");
        assertEquals(GENERAL, service.effectiveFor(product(CAPS, null, null)), "another branch is not affected");

        categoriesById.get(TEES).setSizeChartId(KIDS);
        assertEquals(KIDS, service.effectiveFor(product(DROP, null, null)), "the nearer category wins");
        assertEquals(MENS_TEE, service.effectiveFor(product(MEN, null, null)));
    }

    @Test
    void aProductsOwnChartWinsOverItsCategory() {
        categoriesById.get(MEN).setSizeChartId(MENS_TEE);
        assertEquals(KIDS, service.effectiveFor(product(DROP, KIDS, null)));
    }

    @Test
    void regularFitShowsItsOwnChartWhenTheProductNamesOneAndTheProductsChartOtherwise() {
        categoriesById.get(MEN).setSizeChartId(MENS_TEE);
        assertEquals(MENS_TEE, service.effectiveRegularFitFor(product(DROP, null, null)));
        assertEquals(KIDS, service.effectiveRegularFitFor(product(DROP, KIDS, null)));
        Product both = product(DROP, KIDS, REGULAR);
        assertEquals(REGULAR, service.effectiveRegularFitFor(both));
        assertEquals(KIDS, service.effectiveFor(both), "and the rest of the product keeps its own chart");
    }

    @Test
    void aChartThatWasDeletedIsNeverShown() {
        chartsById.get(KIDS).setDeleted(true);
        categoriesById.get(MEN).setSizeChartId(MENS_TEE);
        assertEquals(MENS_TEE, service.effectiveFor(product(DROP, KIDS, null)), "falls through to the category");
        chartsById.get(MENS_TEE).setDeleted(true);
        assertEquals(GENERAL, service.effectiveFor(product(DROP, KIDS, null)), "then to the default");
    }

    @Test
    void aShopWithNoChartsShowsNone() {
        chartsById.clear();
        assertNull(service.effectiveFor(product(CAPS, null, null)));
    }

    // ── the library ───────────────────────────────────────────────────────────

    @Test
    void chartsAreAddedAndListedByNameWithTheirContents() {
        SizeChartLibraryDTO made = service.create(request("Cap", false));
        assertEquals("Cap", made.getName());
        assertFalse(made.getIsDefault());
        assertEquals(List.of("Chest", "Length"), made.getChart().getColumns());
        assertEquals(new BigDecimal("101"), made.getChart().getRows().get(0).getCells().get(0).getCm());
        assertEquals(List.of("Cap", "General", "Kids", "Men's T-Shirt", "Regular Fit"),
                service.list().stream().map(SizeChartLibraryDTO::getName).toList());
    }

    @Test
    void thereIsExactlyOneDefault() {
        service.setDefault(KIDS);
        assertTrue(chartsById.get(KIDS).getIsDefault());
        assertFalse(chartsById.get(GENERAL).getIsDefault());
        assertEquals(KIDS, service.effectiveFor(product(CAPS, null, null)));

        SizeChartLibraryDTO made = service.create(request("New default", true));
        assertTrue(made.getIsDefault());
        assertEquals(1, service.list().stream().filter(SizeChartLibraryDTO::getIsDefault).count());
    }

    @Test
    void theFirstChartOfAnEmptyLibraryBecomesTheDefault() {
        chartsById.clear();
        assertTrue(service.create(request("Only one", false)).getIsDefault());
    }

    @Test
    void aChartNeedsANameAndAtLeastOneColumn() {
        assertThrows(InvalidRequestException.class, () -> service.create(request("   ", false)));
        SizeChartLibraryDTO noColumns = request("Empty", false);
        noColumns.getChart().setColumns(new ArrayList<>());
        assertThrows(InvalidRequestException.class, () -> service.create(noColumns));
        assertThrows(ResourceNotFoundException.class, () -> service.update(999L, request("Ghost", false)));
    }

    @Test
    void deletingAChartInUseLetsItsProductsAndCategoriesGo() {
        when(charts.countProductsUsing(MENS_TEE)).thenReturn(5L);
        when(charts.countCategoriesUsing(MENS_TEE)).thenReturn(2L);

        assertEquals(7, service.delete(MENS_TEE));

        verify(charts).clearFromProducts(MENS_TEE);
        verify(charts).clearRegularFitFromProducts(MENS_TEE);
        verify(charts).clearFromCategories(MENS_TEE);
        assertTrue(chartsById.get(MENS_TEE).getDeleted());
        assertFalse(service.list().stream().anyMatch(c -> c.getName().equals("Men's T-Shirt")));
    }

    @Test
    void deletingTheDefaultHandsTheDefaultToAnotherChart() {
        service.delete(GENERAL);
        assertEquals(1, service.list().stream().filter(SizeChartLibraryDTO::getIsDefault).count());
        assertFalse(Boolean.TRUE.equals(chartsById.get(GENERAL).getIsDefault()));
    }

    @Test
    void aFormCanOnlyNameAChartThatExists() {
        assertNull(service.checked(null));
        assertNull(service.checked(0L), "0 means none of its own");
        assertEquals(KIDS, service.checked(KIDS));
        assertThrows(InvalidRequestException.class, () -> service.checked(999L));
        chartsById.get(KIDS).setDeleted(true);
        assertThrows(InvalidRequestException.class, () -> service.checked(KIDS));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<SizeChart> live() {
        return chartsById.values().stream().filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                .sorted(Comparator.comparing((SizeChart c) -> c.getName().toLowerCase()).thenComparing(SizeChart::getId)).toList();
    }

    private void chart(long id, String name, boolean isDefault) {
        SizeChart c = new SizeChart();
        c.setId(id);
        c.setName(name);
        c.setChartJson("{\"columns\":[\"Chest\"],\"rows\":[]}");
        c.setIsDefault(isDefault);
        c.setDeleted(false);
        chartsById.put(id, c);
    }

    private void category(long id, Long parentId) {
        Category c = new Category();
        c.setId(id);
        c.setName("Category " + id);
        c.setParentId(parentId);
        c.setDeleted(false);
        categoriesById.put(id, c);
    }

    private static Product product(Long categoryId, Long chartId, Long regularFitChartId) {
        Product p = new Product();
        p.setCategoryId(categoryId);
        p.setSizeChartId(chartId);
        p.setRegularFitSizeChartId(regularFitChartId);
        return p;
    }

    private static SizeChartLibraryDTO request(String name, boolean isDefault) {
        SizeChartDTO chart = new SizeChartDTO(new ArrayList<>(List.of("Chest", "Length")), new ArrayList<>(List.of(
                new SizeChartRowDTO("M", List.of(new SizeChartCellDTO(new BigDecimal("101"), new BigDecimal("39.8")),
                        new SizeChartCellDTO(new BigDecimal("70"), new BigDecimal("27.6")))))));
        return SizeChartLibraryDTO.builder().name(name).chart(chart).isDefault(isDefault).build();
    }
}
