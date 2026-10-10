package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.response.product.ShopOrderItemDTO;
import kn.org.deliverybackend.entity.Category;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The admin's order of products in the shop: saved as one list, first to last;
 * anything left out of the list is no longer ranked.
 */
class ShopOrderTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ShopOrderService service = new ShopOrderService(products, categories);
    private final Map<Long, Product> stored = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        for (long id = 1; id <= 6; id++) stored.put(id, product(id, "Product " + id));
        stored.get(6L).setDeleted(true);

        when(products.findRanked()).thenAnswer(inv -> stored.values().stream()
                .filter(p -> p.getShopRank() != null && !Boolean.TRUE.equals(p.getDeleted()))
                .sorted(Comparator.comparing(Product::getShopRank).thenComparing(Product::getId)).toList());
        when(products.findLiveIds(any())).thenAnswer(inv -> inv.<Collection<Long>>getArgument(0).stream()
                .filter(id -> stored.containsKey(id) && !Boolean.TRUE.equals(stored.get(id).getDeleted())).toList());
        when(products.clearShopRanks()).thenAnswer(inv -> {
            stored.values().forEach(p -> p.setShopRank(null));
            return 0;
        });
        when(products.setShopRank(anyLong(), anyInt())).thenAnswer(inv -> {
            stored.get(inv.<Long>getArgument(0)).setShopRank(inv.getArgument(1));
            return 1;
        });
        Category tees = new Category();
        tees.setId(10L);
        tees.setName("T-Shirts");
        when(categories.findAllById(any())).thenReturn(List.of(tees));
    }

    @Test
    void theListGivenBecomesTheOrderFirstToLast() {
        List<ShopOrderItemDTO> saved = service.set(List.of(4L, 2L, 5L));

        assertEquals(List.of(4L, 2L, 5L), ids(saved));
        assertEquals(List.of(1, 2, 3), saved.stream().map(ShopOrderItemDTO::position).toList());
        assertEquals(1, stored.get(4L).getShopRank());
        assertEquals(3, stored.get(5L).getShopRank());
        assertNull(stored.get(1L).getShopRank(), "a product not in the list is not ranked");
        assertEquals("T-Shirts", saved.get(0).categoryName());
        assertEquals("P-4", saved.get(0).productCode());
    }

    @Test
    void aNewListReplacesTheOldOneSoMovingAndRemovingAreJustANewList() {
        service.set(List.of(1L, 2L, 3L));

        // "Put 3 first" and "remove 2" in one go.
        assertEquals(List.of(3L, 1L), ids(service.set(List.of(3L, 1L))));
        assertNull(stored.get(2L).getShopRank(), "2 was left out, so it is no longer ranked");

        assertTrue(service.set(List.of()).isEmpty(), "an empty list clears the ranking");
        assertTrue(stored.values().stream().allMatch(p -> p.getShopRank() == null));
        assertTrue(service.set(null).isEmpty());
    }

    @Test
    void aProductNamedTwiceCountsOnceWhereItFirstAppears() {
        assertEquals(List.of(2L, 1L, 3L), ids(service.set(Arrays.asList(2L, 1L, 2L, null, 3L, 1L))));
    }

    @Test
    void aDeletedOrUnknownProductIsRefusedAndNothingChanges() {
        service.set(List.of(1L, 2L));

        InvalidRequestException deleted = assertThrows(InvalidRequestException.class, () -> service.set(List.of(3L, 6L)));
        assertTrue(deleted.getMessage().contains("no longer exists"), deleted.getMessage());
        assertThrows(InvalidRequestException.class, () -> service.set(List.of(99L)));
        assertEquals(List.of(1L, 2L), ids(service.list()), "the order saved before is untouched");
    }

    @Test
    void aRankedProductThatIsLaterDeletedLeavesNoGapInThePositions() {
        service.set(List.of(1L, 2L, 3L));
        stored.get(2L).setDeleted(true);

        List<ShopOrderItemDTO> shown = service.list();
        assertEquals(List.of(1L, 3L), ids(shown));
        assertEquals(List.of(1, 2), shown.stream().map(ShopOrderItemDTO::position).toList());
    }

    @Test
    void noMoreThanFiveHundredProductsCanBeRanked() {
        List<Long> tooMany = new ArrayList<>(LongStream.rangeClosed(1, ShopOrderService.MAX_RANKED + 1L).boxed().toList());
        InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> service.set(tooMany));
        assertTrue(e.getMessage().contains("500"), e.getMessage());
    }

    @Test
    void theListSaysWhatTheAdminNeedsToRecogniseAProduct() {
        stored.get(1L).setStockQuantity(0);
        stored.get(1L).setIsAvailable(false);
        ShopOrderItemDTO item = service.set(List.of(1L)).get(0);

        assertEquals("Product 1", item.name());
        assertEquals("SKU-1", item.sku());
        assertEquals(0, item.stockQuantity());
        assertEquals(new BigDecimal("500"), item.price());
        assertEquals(false, item.available());
    }

    private static List<Long> ids(List<ShopOrderItemDTO> items) {
        return items.stream().map(ShopOrderItemDTO::id).toList();
    }

    private static Product product(long id, String name) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setProductCode("P-" + id);
        p.setSku("SKU-" + id);
        p.setCategoryId(10L);
        p.setPrice(new BigDecimal("500"));
        p.setStockQuantity(5);
        p.setIsAvailable(true);
        p.setDeleted(false);
        return p;
    }
}
