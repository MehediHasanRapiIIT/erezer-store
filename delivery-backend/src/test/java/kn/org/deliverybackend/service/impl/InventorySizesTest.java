package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.request.product.AdminStockUpdateRequestDTO;
import kn.org.deliverybackend.dto.request.product.BulkStockAdjustRequestDTO;
import kn.org.deliverybackend.dto.request.product.SizeStockUpdateRequestDTO;
import kn.org.deliverybackend.dto.response.product.BulkStockResultDTO;
import kn.org.deliverybackend.dto.response.product.StockResponseDTO;
import kn.org.deliverybackend.entity.Inventory;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.Variant;
import kn.org.deliverybackend.enumeration.StockOperation;
import kn.org.deliverybackend.enumeration.StockScope;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.InventoryRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.VariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Stock by size on the Inventory page: a figure is per size for a product sold
 * in sizes, and the product's own stock is always the total of its sizes.
 */
class InventorySizesTest {

    private final InventoryRepository inventories = mock(InventoryRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final VariantRepository variants = mock(VariantRepository.class);
    private final InventoryServiceImpl service = new InventoryServiceImpl(
            inventories, products, mock(ApplicationEventPublisher.class), categories, variants);

    private final Map<Long, Product> productById = new HashMap<>();
    private final Map<Long, Inventory> inventoryById = new HashMap<>();
    private final Map<Long, List<Variant>> sizesById = new HashMap<>();
    private long nextVariantId = 100;

    /** A tee sold in S, M and L, and a cap sold as it is. */
    private static final long TEE = 1, CAP = 2;

    @BeforeEach
    void setUp() {
        product(TEE, "Tee", 0);
        size(TEE, "S", 5);
        size(TEE, "M", 8);
        size(TEE, "L", 2);
        product(CAP, "Cap", 30);

        when(products.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(productById.get(inv.<Long>getArgument(0))));
        when(products.findAll()).thenAnswer(inv -> new ArrayList<>(productById.values()));
        when(products.findAllById(any())).thenAnswer(inv -> {
            List<Product> found = new ArrayList<>();
            for (Long id : inv.<Iterable<Long>>getArgument(0)) found.add(productById.get(id));
            return found;
        });
        when(inventories.findByProductIdWithLock(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(inventoryById.get(inv.<Long>getArgument(0))));
        when(variants.findByProductId(anyLong()))
                .thenAnswer(inv -> sizesById.getOrDefault(inv.<Long>getArgument(0), List.of()));
        when(variants.sumStockByProduct(anyLong())).thenAnswer(inv ->
                (long) sizesById.getOrDefault(inv.<Long>getArgument(0), List.of()).stream()
                        .mapToInt(Variant::getStockQuantity).sum());
    }

    private void product(long id, String name, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setStockQuantity(stock);
        p.setDeleted(false);
        productById.put(id, p);
        Inventory i = new Inventory();
        i.setProductId(id);
        i.setStockQuantity(stock);
        inventoryById.put(id, i);
    }

    private void size(long productId, String size, int stock) {
        Variant v = new Variant();
        v.setId(nextVariantId++);
        v.setProductId(productId);
        v.setSize(size);
        v.setStockQuantity(stock);
        v.setDeleted(false);
        sizesById.computeIfAbsent(productId, k -> new ArrayList<>()).add(v);
    }

    private int stockOf(long productId, String size) {
        return sizesById.get(productId).stream().filter(v -> v.getSize().equals(size)).findFirst().orElseThrow().getStockQuantity();
    }

    private int productStock(long id) {
        assertEquals(inventoryById.get(id).getStockQuantity(), productById.get(id).getStockQuantity(),
                "the two copies of a product's stock agree");
        return inventoryById.get(id).getStockQuantity();
    }

    private BulkStockResultDTO everyProduct(StockOperation operation, int quantity, String... sizes) {
        return service.adjustStock(new BulkStockAdjustRequestDTO(
                StockScope.ALL, null, null, operation, quantity, sizes.length == 0 ? null : List.of(sizes)));
    }

    // ── one product ───────────────────────────────────────────────────────────

    @Test
    void anExactFigureForEachSizeAndTheProductIsTheirTotal() {
        SizeStockUpdateRequestDTO request = new SizeStockUpdateRequestDTO();
        List<SizeStockUpdateRequestDTO.Item> items = new ArrayList<>();
        for (Variant v : sizesById.get(TEE)) {
            SizeStockUpdateRequestDTO.Item item = new SizeStockUpdateRequestDTO.Item();
            item.setVariantId(v.getId());
            item.setQuantity(v.getSize().equals("M") ? 20 : 3);
            items.add(item);
        }
        request.setSizes(items);

        StockResponseDTO row = service.setSizeStock(TEE, request);

        assertEquals(20, stockOf(TEE, "M"));
        assertEquals(26, productStock(TEE));
        assertEquals(26, row.getStockQuantity());
        assertEquals(List.of("S", "M", "L"), row.getSizes().stream().map(StockResponseDTO.SizeStock::getSize).toList());
    }

    @Test
    void aSizeOfAnotherProductIsRefusedAndNothingChanges() {
        SizeStockUpdateRequestDTO.Item item = new SizeStockUpdateRequestDTO.Item();
        item.setVariantId(9999L);
        item.setQuantity(50);
        SizeStockUpdateRequestDTO request = new SizeStockUpdateRequestDTO();
        request.setSizes(List.of(item));

        assertThrows(InvalidRequestException.class, () -> service.setSizeStock(TEE, request));
        assertEquals(5, stockOf(TEE, "S"));
    }

    @Test
    void theOrdinaryStockUpdateIsPerSizeForAProductSoldInSizes() {
        AdminStockUpdateRequestDTO request = new AdminStockUpdateRequestDTO();
        request.setOperation(StockOperation.SET);
        request.setQuantity(10);
        service.updateStock(TEE, request);
        assertEquals(10, stockOf(TEE, "S"));
        assertEquals(10, stockOf(TEE, "L"));
        assertEquals(30, productStock(TEE));
    }

    // ── many products ─────────────────────────────────────────────────────────

    @Test
    void addingToEveryProductAddsToEachSizeAndToProductsWithoutSizes() {
        BulkStockResultDTO result = everyProduct(StockOperation.INCREMENT, 10);
        assertEquals(15, stockOf(TEE, "S"));
        assertEquals(18, stockOf(TEE, "M"));
        assertEquals(12, stockOf(TEE, "L"));
        assertEquals(45, productStock(TEE));
        assertEquals(40, productStock(CAP));
        assertEquals(2, result.getUpdated());
        assertTrue(result.getMessage().contains("per size"), result.getMessage());
    }

    @Test
    void namedSizesOnlyLeaveTheOtherSizesAndProductsWithoutSizesAlone() {
        BulkStockResultDTO result = everyProduct(StockOperation.SET, 50, "m", "L");
        assertEquals(5, stockOf(TEE, "S"));
        assertEquals(50, stockOf(TEE, "M"));
        assertEquals(50, stockOf(TEE, "L"));
        assertEquals(105, productStock(TEE));
        assertEquals(30, productStock(CAP));
        assertEquals(1, result.getUpdated());
        assertTrue(result.getMessage().contains("left as it was"), result.getMessage());
    }

    @Test
    void removingMoreThanASizeHasLeavesThatSizeAtZero() {
        BulkStockResultDTO result = everyProduct(StockOperation.DECREMENT, 6);
        assertEquals(0, stockOf(TEE, "S"));
        assertEquals(2, stockOf(TEE, "M"));
        assertEquals(0, stockOf(TEE, "L"));
        assertEquals(2, productStock(TEE));
        assertEquals(24, productStock(CAP));
        assertEquals(1, result.getSetToZero());
    }

    @Test
    void aSizeNoProductHasChangesNothingAndSaysSo() {
        assertThrows(InvalidRequestException.class, () -> everyProduct(StockOperation.SET, 9, "XXL"));
        assertEquals(15, stockOf(TEE, "S") + stockOf(TEE, "M") + stockOf(TEE, "L"));
        assertEquals(30, productStock(CAP));
    }
}
