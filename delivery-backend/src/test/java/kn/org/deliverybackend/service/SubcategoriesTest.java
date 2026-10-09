package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.request.category.CategoryRequestDTO;
import kn.org.deliverybackend.dto.response.category.CategoryResponseDTO;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.mapper.CategoryMapper;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.service.impl.CategoryServiceImpl;
import kn.org.deliverybackend.service.impl.ShippingCharges;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Subcategories: two levels only, a main category covers what is in its
 * subcategories, and a subcategory follows its parent unless it sets its own.
 */
class SubcategoriesTest {

    private static final long HOODIES = 1, ZIP = 2, PULLOVER = 3, CAPS = 4, HALF_ZIP = 5, KIDS_HALF_ZIP = 6;

    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryMapper mapper = mock(CategoryMapper.class);
    private final CategoryServiceImpl service = new CategoryServiceImpl(categories, mapper, products,
            mock(kn.org.deliverybackend.service.impl.SizeChartLibraryService.class));
    private final Map<Long, Category> byId = new HashMap<>();

    @BeforeEach
    void setUp() {
        category(HOODIES, "Hoodies", null);
        category(ZIP, "Zip Hoodies", HOODIES);
        category(PULLOVER, "Pullover Hoodies", HOODIES);
        category(CAPS, "Caps", null);

        when(categories.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(byId.get(inv.<Long>getArgument(0))));
        when(categories.findByParentIdAndDeletedFalse(anyLong())).thenAnswer(inv -> byId.values().stream()
                .filter(c -> inv.getArgument(0).equals(c.getParentId())).toList());
        when(categories.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            if (c.getId() == null) c.setId(50L);   // as the database would
            return c;
        });
        when(mapper.toEntity(any(CategoryRequestDTO.class))).thenAnswer(inv -> {
            CategoryRequestDTO dto = inv.getArgument(0);
            Category c = new Category();
            c.setName(dto.getName());
            c.setParentId(dto.getParentId());
            return c;
        });
        when(mapper.toResponseDTO(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            CategoryResponseDTO dto = new CategoryResponseDTO();
            dto.setId(c.getId());
            dto.setName(c.getName());
            dto.setParentId(c.getParentId());
            dto.setShowOnHome(c.getShowOnHome());
            return dto;
        });
        when(products.findByCategoryIdIn(any())).thenAnswer(inv -> {
            // Two products in each category.
            List<Product> found = new ArrayList<>();
            for (Long ignored : inv.<Collection<Long>>getArgument(0)) {
                found.add(new Product());
                found.add(new Product());
            }
            return found;
        });
    }

    private Category category(long id, String name, Long parentId) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        c.setParentId(parentId);
        c.setDeleted(false);
        byId.put(id, c);
        return c;
    }

    private static CategoryRequestDTO request(String name, Long parentId) {
        CategoryRequestDTO dto = new CategoryRequestDTO();
        dto.setName(name);
        dto.setIsActive(true);
        dto.setParentId(parentId);
        return dto;
    }

    // ── the tree ──────────────────────────────────────────────────────────────

    @Test
    void aMainCategoryCoversItsSubcategoriesAndASubcategoryOnlyItself() {
        assertEquals(Set.of(HOODIES, ZIP, PULLOVER), CategoryTree.family(categories, HOODIES));
        assertEquals(Set.of(ZIP), CategoryTree.family(categories, ZIP));
        assertEquals(Set.of(CAPS), CategoryTree.family(categories, CAPS));
        assertTrue(CategoryTree.family(categories, null).isEmpty());
    }

    @Test
    void aMainCategoryCountsTheProductsInItsSubcategories() {
        CategoryResponseDTO hoodies = service.getCategoryById(HOODIES);
        assertEquals(6, hoodies.getProductCount());
        assertEquals(2, hoodies.getSubcategoryCount());
        CategoryResponseDTO zip = service.getCategoryById(ZIP);
        assertEquals(2, zip.getProductCount());
        assertEquals("Hoodies", zip.getParentName());
    }

    // ── what a subcategory takes from its parent ──────────────────────────────

    @Test
    void theDeliveryChargeComesFromTheParentUnlessTheSubcategorySetsItsOwn() {
        byId.get(HOODIES).setShippingCharge(new BigDecimal("120"));
        assertEquals(new BigDecimal("120"), CategoryTree.shippingCharge(categories, byId.get(ZIP)));

        byId.get(ZIP).setShippingCharge(BigDecimal.ZERO);
        assertEquals(BigDecimal.ZERO, CategoryTree.shippingCharge(categories, byId.get(ZIP)), "its own, even free delivery, wins");
        assertNull(CategoryTree.shippingCharge(categories, byId.get(CAPS)));

        // And that is what checkout charges a product in the subcategory.
        Product inPullover = new Product();
        inPullover.setCategoryId(PULLOVER);
        assertEquals(new BigDecimal("120"), new ShippingCharges(categories).forProduct(inPullover));
    }

    @Test
    void neverDiscountOnTheParentKeepsItsSubcategoriesAtFullPriceToo() {
        assertFalse(CategoryTree.discountExcluded(categories, byId.get(ZIP)));
        byId.get(HOODIES).setDiscountExcluded(true);
        assertTrue(CategoryTree.discountExcluded(categories, byId.get(ZIP)));
        assertFalse(CategoryTree.discountExcluded(categories, byId.get(CAPS)));
    }

    @Test
    void showingStockAsANumberFollowsTheParent() {
        byId.get(HOODIES).setShowStockQuantity(true);
        assertEquals(true, CategoryTree.showStockQuantity(categories, byId.get(ZIP)));
        assertEquals("Hoodies › Zip Hoodies", CategoryTree.path(categories, byId.get(ZIP)));
        assertEquals("Caps", CategoryTree.path(categories, byId.get(CAPS)));
    }

    // ── any depth ─────────────────────────────────────────────────────────────

    @Test
    void aSubcategoryCanBeAddedUnderAMainCategory() {
        CategoryResponseDTO made = service.createCategory(request("Oversized Hoodies", HOODIES));
        assertEquals(HOODIES, made.getParentId());
    }

    @Test
    void aSubcategoryCanHaveSubcategoriesOfItsOwnToAnyDepth() {
        CategoryResponseDTO half = service.createCategory(request("Half Zip", ZIP));
        assertEquals(ZIP, half.getParentId());

        category(HALF_ZIP, "Half Zip", ZIP);
        category(KIDS_HALF_ZIP, "Kids Half Zip", HALF_ZIP);
        assertEquals(Set.of(HOODIES, ZIP, PULLOVER, HALF_ZIP, KIDS_HALF_ZIP), CategoryTree.family(categories, HOODIES));
        assertEquals(Set.of(ZIP, HALF_ZIP, KIDS_HALF_ZIP), CategoryTree.family(categories, ZIP));
        assertEquals(Set.of(KIDS_HALF_ZIP), CategoryTree.family(categories, KIDS_HALF_ZIP));

        CategoryResponseDTO deepest = service.getCategoryById(KIDS_HALF_ZIP);
        assertEquals(3, deepest.getDepth());
        assertEquals("Hoodies › Zip Hoodies › Half Zip › Kids Half Zip", deepest.getPath());
        assertEquals("Half Zip", deepest.getParentName());
        assertEquals(0, service.getCategoryById(HOODIES).getDepth());
        assertEquals(4, service.getCategoryById(HOODIES).getSubcategoryCount(), "everything under it, at every level");
        assertEquals(10, service.getCategoryById(HOODIES).getProductCount());
    }

    @Test
    void aDeepSubcategoryTakesEachSettingFromTheNearestCategoryAboveThatSetsIt() {
        category(HALF_ZIP, "Half Zip", ZIP);
        category(KIDS_HALF_ZIP, "Kids Half Zip", HALF_ZIP);
        Category deepest = byId.get(KIDS_HALF_ZIP);

        byId.get(HOODIES).setShippingCharge(new BigDecimal("120"));
        assertEquals(new BigDecimal("120"), CategoryTree.shippingCharge(categories, deepest), "from the main category, three levels up");
        byId.get(ZIP).setShippingCharge(new BigDecimal("80"));
        assertEquals(new BigDecimal("80"), CategoryTree.shippingCharge(categories, deepest), "the nearer one wins");
        assertEquals(new BigDecimal("120"), CategoryTree.shippingCharge(categories, byId.get(PULLOVER)), "another branch is not affected");

        assertFalse(CategoryTree.discountExcluded(categories, deepest));
        byId.get(HOODIES).setDiscountExcluded(true);
        assertTrue(CategoryTree.discountExcluded(categories, deepest));

        byId.get(ZIP).setShowStockQuantity(true);
        assertEquals(true, CategoryTree.showStockQuantity(categories, deepest));
        assertFalse(Boolean.TRUE.equals(CategoryTree.showStockQuantity(categories, byId.get(PULLOVER))));
    }

    @Test
    void aCategoryMovesUnderAnotherWithEverythingUnderIt() {
        CategoryResponseDTO moved = service.updateCategory(HOODIES, request("Hoodies", CAPS));
        assertEquals(CAPS, moved.getParentId());
        assertEquals(Set.of(CAPS, HOODIES, ZIP, PULLOVER), CategoryTree.family(categories, CAPS));
        assertEquals("Caps › Hoodies › Zip Hoodies", CategoryTree.path(categories, byId.get(ZIP)));
    }

    @Test
    void aCategoryCannotBePutUnderSomethingInsideItself() {
        category(HALF_ZIP, "Half Zip", ZIP);
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> service.updateCategory(HOODIES, request("Hoodies", HALF_ZIP)));
        assertTrue(e.getMessage().contains("inside this category"), e.getMessage());
        assertNull(byId.get(HOODIES).getParentId());
        verify(categories, never()).save(any());
    }

    @Test
    void aLoopInTheDataNeverHangsAnything() {
        byId.get(HOODIES).setParentId(ZIP);   // as only bad data could be
        when(categories.findAll()).thenReturn(new ArrayList<>(byId.values()));
        assertEquals(Set.of(HOODIES, ZIP, PULLOVER), CategoryTree.family(categories, HOODIES));
        assertEquals(1, CategoryTree.depth(categories, byId.get(ZIP)));
        assertFalse(CategoryTree.discountExcluded(categories, byId.get(ZIP)));
        assertEquals(4, service.getAllCategories().size(), "and every category is still listed");
    }

    @Test
    void listsComeInTreeOrder() {
        category(HALF_ZIP, "Half Zip", ZIP);
        category(KIDS_HALF_ZIP, "Kids Half Zip", HALF_ZIP);
        when(categories.findAll()).thenReturn(new ArrayList<>(byId.values()));
        assertEquals(List.of("Caps", "Hoodies", "Pullover Hoodies", "Zip Hoodies", "Half Zip", "Kids Half Zip"),
                service.getAllCategories().stream().map(CategoryResponseDTO::getName).toList());
        assertEquals(List.of(0, 0, 1, 1, 2, 3),
                service.getAllCategories().stream().map(CategoryResponseDTO::getDepth).toList());
    }

    @Test
    void aCategoryCannotBeItsOwnSubcategory() {
        assertThrows(InvalidRequestException.class, () -> service.updateCategory(CAPS, request("Caps", CAPS)));
    }

    @Test
    void aSubcategoryNeverGetsALandingPageSection() {
        CategoryRequestDTO dto = request("Zip Hoodies", HOODIES);
        dto.setShowOnHome(true);
        assertFalse(Boolean.TRUE.equals(service.updateCategory(ZIP, dto).getShowOnHome()));
    }

    @Test
    void aCategoryWithSubcategoriesIsNotDeleted() {
        InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> service.deleteCategory(HOODIES));
        assertTrue(e.getMessage().contains("2 subcategories"), e.getMessage());
        verify(categories, never()).deleteById(anyLong());
        service.deleteCategory(ZIP);
        verify(categories).deleteById(ZIP);
    }
}
