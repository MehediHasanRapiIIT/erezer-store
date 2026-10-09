package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.request.product.BulkProductActionDTO;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.enumeration.BulkProductAction;
import kn.org.deliverybackend.enumeration.StockDisplay;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.service.impl.ProductBulkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One action for the products ticked on the Products page: it changes those
 * products, only those, and nothing at all when the request cannot be done in full.
 */
class ProductBulkTest {

    private static final long TSHIRTS = 1, HOODIES = 2, REMOVED = 3;

    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ProductService productService = mock(ProductService.class);
    private final kn.org.deliverybackend.service.impl.SizeChartLibraryService sizeCharts =
            mock(kn.org.deliverybackend.service.impl.SizeChartLibraryService.class);
    private final ProductBulkService service = new ProductBulkService(products, categories, productService, sizeCharts);
    private final Map<Long, Product> byId = new HashMap<>();

    @BeforeEach
    void setUp() {
        product(10, TSHIRTS, "TS-1001");
        product(11, TSHIRTS, "TS-1002");
        product(12, HOODIES, "HD-1001");
        product(13, TSHIRTS, "TS-1003");   // never ticked
        Product gone = product(14, TSHIRTS, "TS-1004");
        gone.setDeleted(true);

        when(products.findAllById(any())).thenAnswer(inv -> {
            List<Product> found = new ArrayList<>();
            for (Long id : inv.<Iterable<Long>>getArgument(0)) if (byId.containsKey(id)) found.add(byId.get(id));
            return found;
        });
        when(categories.findById(anyLong())).thenAnswer(inv -> {
            long id = inv.getArgument(0);
            if (id != TSHIRTS && id != HOODIES && id != REMOVED) return Optional.empty();
            Category c = new Category();
            c.setId(id);
            c.setName(id == TSHIRTS ? "T-Shirts" : id == HOODIES ? "Hoodies" : "Removed");
            c.setDeleted(id == REMOVED);
            return Optional.of(c);
        });
    }

    @Test
    void movesTheTickedProductsAndKeepsTheirCodes() {
        ProductBulkService.Result result = run(BulkProductAction.MOVE_CATEGORY, HOODIES, 10L, 11L, 12L);

        assertEquals(HOODIES, byId.get(10L).getCategoryId());
        assertEquals(HOODIES, byId.get(11L).getCategoryId());
        assertEquals("TS-1001", byId.get(10L).getProductCode());
        assertEquals("TS-1002", byId.get(11L).getProductCode());
        assertEquals(TSHIRTS, byId.get(13L).getCategoryId(), "a product that was not ticked stays where it is");
        assertEquals(2, result.changed());
        assertEquals("2 products moved to Hoodies. 1 was already there.", result.message());
    }

    @Test
    void movingNeedsACategoryThatExists() {
        assertThrows(InvalidRequestException.class, () -> run(BulkProductAction.MOVE_CATEGORY, null, 10L));
        assertThrows(ResourceNotFoundException.class, () -> run(BulkProductAction.MOVE_CATEGORY, 99L, 10L));
        assertThrows(ResourceNotFoundException.class, () -> run(BulkProductAction.MOVE_CATEGORY, REMOVED, 10L));
        assertEquals(TSHIRTS, byId.get(10L).getCategoryId());
        verify(products, never()).saveAll(any());
    }

    @Test
    void deletesEachTickedProductTheWayOneIsDeleted() {
        ProductBulkService.Result result = run(BulkProductAction.DELETE, null, 10L, 12L);

        verify(productService).deleteProduct(10L);
        verify(productService).deleteProduct(12L);
        verify(productService, never()).deleteProduct(11L);
        verify(productService, never()).deleteProduct(13L);
        assertEquals("2 products deleted.", result.message());
    }

    @Test
    void flipsEachSwitchOnTheTickedProductsOnly() {
        run(BulkProductAction.HIDE, null, 10L, 11L);
        assertFalse(byId.get(10L).getIsAvailable());
        assertFalse(byId.get(11L).getIsAvailable());
        assertTrue(byId.get(13L).getIsAvailable());
        run(BulkProductAction.SHOW, null, 10L);
        assertTrue(byId.get(10L).getIsAvailable());
        assertFalse(byId.get(11L).getIsAvailable());

        run(BulkProductAction.FEATURE, null, 10L);
        assertTrue(byId.get(10L).getIsFeatured());
        run(BulkProductAction.UNFEATURE, null, 10L);
        assertFalse(byId.get(10L).getIsFeatured());

        run(BulkProductAction.NEW_ARRIVAL_ON, null, 12L);
        assertTrue(byId.get(12L).getIsNewArrival());
        run(BulkProductAction.NEW_ARRIVAL_OFF, null, 12L);
        assertFalse(byId.get(12L).getIsNewArrival());

        run(BulkProductAction.NEVER_DISCOUNT_ON, null, 12L);
        assertTrue(byId.get(12L).getDiscountExcluded());
        run(BulkProductAction.NEVER_DISCOUNT_OFF, null, 12L);
        assertFalse(byId.get(12L).getDiscountExcluded());

        run(BulkProductAction.STOCK_SHOW_QUANTITY, null, 11L);
        assertEquals(StockDisplay.QUANTITY, byId.get(11L).getStockDisplay());
        run(BulkProductAction.STOCK_SHOW_LABELS, null, 11L);
        assertEquals(StockDisplay.LABEL, byId.get(11L).getStockDisplay());
    }

    @Test
    void doesNothingAtAllWhenATickedProductIsGone() {
        InvalidRequestException missing = assertThrows(InvalidRequestException.class,
                () -> run(BulkProductAction.HIDE, null, 10L, 99L));
        assertTrue(missing.getMessage().startsWith("1 of the ticked products no longer exists."));
        assertThrows(InvalidRequestException.class, () -> run(BulkProductAction.DELETE, null, 10L, 14L));

        assertTrue(byId.get(10L).getIsAvailable());
        verify(productService, never()).deleteProduct(anyLong());
        verify(products, never()).saveAll(any());
    }

    @Test
    void givesTheTickedProductsOneSizeChartOrLetsThemFollowTheirCategoryAgain() {
        when(sizeCharts.checked(7L)).thenReturn(7L);
        ProductBulkService.Result set = service.apply(new BulkProductActionDTO(BulkProductAction.SET_SIZE_CHART, List.of(10L, 11L), null, 7L));
        assertEquals(7L, byId.get(10L).getSizeChartId());
        assertEquals(7L, byId.get(11L).getSizeChartId());
        assertEquals(null, byId.get(13L).getSizeChartId());
        assertEquals("2 products now use the chosen size chart.", set.message());

        when(sizeCharts.checked(0L)).thenReturn(null);
        ProductBulkService.Result cleared = service.apply(new BulkProductActionDTO(BulkProductAction.SET_SIZE_CHART, List.of(10L), null, 0L));
        assertEquals(null, byId.get(10L).getSizeChartId());
        assertEquals(7L, byId.get(11L).getSizeChartId());
        assertEquals("1 product now follows its category's size chart.", cleared.message());
    }

    @Test
    void needsAtLeastOneProductAndCountsATickOnce() {
        assertThrows(InvalidRequestException.class, () -> service.apply(new BulkProductActionDTO(BulkProductAction.HIDE, List.of(), null)));
        assertThrows(InvalidRequestException.class, () -> service.apply(new BulkProductActionDTO(BulkProductAction.HIDE, null, null)));

        assertEquals("1 product hidden from the shop.", run(BulkProductAction.HIDE, null, 10L, 10L).message());
    }

    private ProductBulkService.Result run(BulkProductAction action, Long categoryId, Long... ids) {
        return service.apply(new BulkProductActionDTO(action, List.of(ids), categoryId));
    }

    private Product product(long id, long categoryId, String code) {
        Product p = new Product();
        p.setId(id);
        p.setName("Product " + id);
        p.setCategoryId(categoryId);
        p.setProductCode(code);
        p.setIsAvailable(true);
        p.setIsFeatured(false);
        p.setIsNewArrival(false);
        p.setDiscountExcluded(false);
        byId.put(id, p);
        return p;
    }
}
