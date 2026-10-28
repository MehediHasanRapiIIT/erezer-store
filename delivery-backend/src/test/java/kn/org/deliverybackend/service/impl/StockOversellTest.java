package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.request.order.OrderItemRequestDTO;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.Variant;
import kn.org.deliverybackend.exception.InsufficientStockException;
import kn.org.deliverybackend.repository.VariantRepository;
import kn.org.deliverybackend.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * An order can't sell more of a size than there is, even with that size on two
 * lines: the lines are added up before the stock is checked.
 */
class StockOversellTest {

    private final InventoryService inventory = mock(InventoryService.class);
    private final VariantRepository variants = mock(VariantRepository.class);
    private OrderServiceImpl orders;
    private final Variant blackM = new Variant();

    @BeforeEach
    void setUp() throws Exception {
        // Only the stock check runs here: every other collaborator can stay empty.
        Constructor<?> ctor = OrderServiceImpl.class.getDeclaredConstructors()[0];
        Object[] args = new Object[ctor.getParameterCount()];
        orders = (OrderServiceImpl) ctor.newInstance(args);
        ReflectionTestUtils.setField(orders, "inventoryService", inventory);
        ReflectionTestUtils.setField(orders, "variantRepository", variants);

        Product tee = new Product();
        tee.setId(237L);
        tee.setName("Classic Cotton T-shirt");
        tee.setPrice(new BigDecimal("500"));
        when(inventory.lockAndGetProduct(anyLong())).thenReturn(tee);

        blackM.setId(171L);
        blackM.setProductId(237L);
        blackM.setStockQuantity(3);
        when(variants.findById(171L)).thenReturn(Optional.of(blackM));
        when(variants.saveAndFlush(any(Variant.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static OrderItemRequestDTO line(int qty) {
        OrderItemRequestDTO item = new OrderItemRequestDTO();
        item.setProductId(237L);
        item.setVariantId(171L);
        item.setQuantity(qty);
        return item;
    }

    private Object reserve(List<OrderItemRequestDTO> items) throws Exception {
        Method m = OrderServiceImpl.class.getDeclaredMethod("computeAndReserveStock", List.class, Double.class);
        m.setAccessible(true);
        try {
            return m.invoke(orders, items, null);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    @Test
    void theSameSizeOnTwoLinesMustFitTheStockTogether() {
        // 3 in stock; 2 + 2 = 4 asked for. Each line alone would have passed.
        Exception e = assertThrows(Exception.class, () -> reserve(List.of(line(2), line(2))));
        assertInstanceOf(InsufficientStockException.class, e);
        assertEquals(3, blackM.getStockQuantity(), "nothing was taken off");
    }

    @Test
    void twoLinesThatFitTogetherAreSold() throws Exception {
        reserve(List.of(line(1), line(2)));
        assertEquals(0, blackM.getStockQuantity(), "3 sold, none left, never below zero");
    }
}
