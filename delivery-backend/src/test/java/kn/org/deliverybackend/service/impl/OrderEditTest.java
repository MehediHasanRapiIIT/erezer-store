package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.OrderEditRequestDTO;
import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.OrderItem;
import kn.org.deliverybackend.entity.OrderNote;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.Variant;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.OrderItemRepository;
import kn.org.deliverybackend.repository.OrderNoteRepository;
import kn.org.deliverybackend.repository.OrderRepository;
import kn.org.deliverybackend.repository.VariantRepository;
import kn.org.deliverybackend.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An admin correcting an order: the customer's details, the items (with stock
 * following every change), and the prices, with the total kept right and a
 * note left of what was changed.
 */
class OrderEditTest {

    private static final UUID ORDER = UUID.randomUUID();
    private static final long TEE = 1L, CAP = 2L, WHITE_M = 11L, WHITE_L = 12L;

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final OrderNoteRepository notes = mock(OrderNoteRepository.class);
    private final VariantRepository variants = mock(VariantRepository.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final OrderEditService service = new OrderEditService(orders, items, notes, variants, inventory, mock(ApplicationEventPublisher.class));

    private final Order order = new Order();
    private final Map<UUID, OrderItem> lines = new LinkedHashMap<>();
    private final Map<Long, Variant> sizes = new LinkedHashMap<>();
    private final Map<Long, Product> products = new LinkedHashMap<>();
    private final Map<Long, Integer> plainStock = new LinkedHashMap<>();
    private final List<OrderNote> written = new ArrayList<>();
    private OrderItem teeLine;

    @BeforeEach
    void setUp() {
        // The order: 2 × Tee (White / M) at ৳900 = ৳1,800, delivery ৳120, total ৳1,920.
        order.setId(ORDER);
        order.setOrderStatus("PLACED");
        order.setCustomerName("Rahim");
        order.setCustomerPhone("01700000000");
        order.setCustomerEmail("rahim@example.com");
        order.setDeliveryAddress("1 Test Road, Dhaka");
        order.setSubtotalAmount(new BigDecimal("1800.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setShippingFee(new BigDecimal("120.00"));
        order.setTotalAmount(new BigDecimal("1920.00"));
        order.setDeleted(false);

        products.put(TEE, product(TEE, "Tee", "TS-1", "800"));
        products.put(CAP, product(CAP, "Cap", "CP-1", "400"));
        sizes.put(WHITE_M, variant(WHITE_M, TEE, "M", 2, "900"));
        sizes.put(WHITE_L, variant(WHITE_L, TEE, "L", 3, null));
        plainStock.put(CAP, 5);

        teeLine = new OrderItem();
        teeLine.setId(UUID.randomUUID());
        teeLine.setOrderId(ORDER);
        teeLine.setProductId(TEE);
        teeLine.setProductName("Tee");
        teeLine.setVariantId(WHITE_M);
        teeLine.setVariantName("M");
        teeLine.setQuantity(2);
        teeLine.setPriceAtOrder(new BigDecimal("900.00"));
        lines.put(teeLine.getId(), teeLine);

        when(orders.findById(ORDER)).thenReturn(Optional.of(order));
        when(items.findByOrderId(ORDER)).thenAnswer(inv -> new ArrayList<>(lines.values()));
        when(items.save(any(OrderItem.class))).thenAnswer(inv -> {
            OrderItem line = inv.getArgument(0);
            if (line.getId() == null) line.setId(UUID.randomUUID());
            lines.put(line.getId(), line);
            return line;
        });
        doAnswer(inv -> lines.remove(inv.<OrderItem>getArgument(0).getId())).when(items).delete(any(OrderItem.class));
        when(notes.save(any(OrderNote.class))).thenAnswer(inv -> {
            written.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(variants.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(sizes.get(inv.<Long>getArgument(0))));
        when(variants.findByProductId(anyLong())).thenAnswer(inv -> sizes.values().stream()
                .filter(v -> v.getProductId().equals(inv.<Long>getArgument(0))).toList());
        when(variants.saveAndFlush(any(Variant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inventory.lockAndGetProduct(anyLong())).thenAnswer(inv -> {
            Product p = products.get(inv.<Long>getArgument(0));
            if (p == null) throw new IllegalStateException("no such product");
            return p;
        });
        when(inventory.getAvailableStock(anyLong())).thenAnswer(inv -> plainStock.getOrDefault(inv.<Long>getArgument(0), 0));
        doAnswer(inv -> plainStock.merge(inv.<Product>getArgument(0).getId(), -inv.<Integer>getArgument(1), Integer::sum))
                .when(inventory).decrementStock(any(Product.class), anyInt());
        doAnswer(inv -> plainStock.merge(inv.<Product>getArgument(0).getId(), inv.<Integer>getArgument(1), Integer::sum))
                .when(inventory).incrementStock(any(Product.class), anyInt());
    }

    // ── the customer's details ───────────────────────────────────────────────

    @Test
    void theCustomersDetailsCanBeCorrectedAndANoteSaysWhatChanged() {
        OrderEditRequestDTO edit = new OrderEditRequestDTO();
        edit.setCustomerName("  Rahim Uddin ");
        edit.setCustomerPhone("01811111111");
        edit.setDeliveryAddress("22 New Road, Sylhet");

        List<String> changes = service.edit(ORDER, edit, false, "Ayesha");

        assertEquals("Rahim Uddin", order.getCustomerName());
        assertEquals("01811111111", order.getCustomerPhone());
        assertEquals("22 New Road, Sylhet", order.getDeliveryAddress());
        assertEquals("rahim@example.com", order.getCustomerEmail(), "what was not sent is not touched");
        assertEquals(3, changes.size());
        assertEquals(new BigDecimal("1920.00"), order.getTotalAmount(), "the money is untouched");
        assertEquals(1, written.size());
        assertTrue(written.get(0).getBody().startsWith("Order edited by Ayesha:"), written.get(0).getBody());
        assertTrue(written.get(0).getBody().contains("Phone: 01700000000 → 01811111111"));
    }

    @Test
    void sendingTheSameDetailsBackChangesNothingAndLeavesNoNote() {
        OrderEditRequestDTO edit = new OrderEditRequestDTO();
        edit.setCustomerName("Rahim");
        edit.setItems(List.of(line(teeLine.getId(), 2, "900")));
        edit.setShippingFee(new BigDecimal("120"));

        assertTrue(service.edit(ORDER, edit, false, "Ayesha").isEmpty());
        assertTrue(written.isEmpty());
        verify(orders, never()).save(any());
    }

    @Test
    void badDetailsAreRefused() {
        OrderEditRequestDTO noName = new OrderEditRequestDTO();
        noName.setCustomerName("   ");
        assertThrows(InvalidRequestException.class, () -> service.edit(ORDER, noName, true, "A"));
        OrderEditRequestDTO badPhone = new OrderEditRequestDTO();
        badPhone.setCustomerPhone("call me");
        assertThrows(InvalidRequestException.class, () -> service.edit(ORDER, badPhone, true, "A"));
        OrderEditRequestDTO badEmail = new OrderEditRequestDTO();
        badEmail.setCustomerEmail("not-an-email");
        assertThrows(InvalidRequestException.class, () -> service.edit(ORDER, badEmail, true, "A"));
        OrderEditRequestDTO noEmail = new OrderEditRequestDTO();
        noEmail.setCustomerEmail("");
        service.edit(ORDER, noEmail, true, "A");
        assertNull(order.getCustomerEmail(), "an email can be taken off");
    }

    // ── quantities and stock ─────────────────────────────────────────────────

    @Test
    void aLargerQuantityTakesStockAndASmallerOneGivesItBack() {
        OrderEditRequestDTO more = new OrderEditRequestDTO();
        more.setItems(List.of(line(teeLine.getId(), 4, null)));
        service.edit(ORDER, more, false, "A");
        assertEquals(4, teeLine.getQuantity());
        assertEquals(0, sizes.get(WHITE_M).getStockQuantity(), "2 more taken from the 2 left");
        assertEquals(new BigDecimal("3600.00"), order.getSubtotalAmount());
        assertEquals(new BigDecimal("3720.00"), order.getTotalAmount(), "4 × 900 + 120 delivery");

        OrderEditRequestDTO fewer = new OrderEditRequestDTO();
        fewer.setItems(List.of(line(teeLine.getId(), 1, null)));
        service.edit(ORDER, fewer, false, "A");
        assertEquals(3, sizes.get(WHITE_M).getStockQuantity(), "3 given back");
        assertEquals(new BigDecimal("1020.00"), order.getTotalAmount());
    }

    @Test
    void aQuantityThereIsNoStockForIsRefusedAndNothingChanges() {
        OrderEditRequestDTO tooMany = new OrderEditRequestDTO();
        tooMany.setItems(List.of(line(teeLine.getId(), 5, null)));
        InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> service.edit(ORDER, tooMany, false, "A"));
        assertTrue(e.getMessage().contains("Not enough stock") && e.getMessage().contains("2 left"), e.getMessage());
        assertEquals(2, teeLine.getQuantity());
        assertEquals(2, sizes.get(WHITE_M).getStockQuantity());
    }

    @Test
    void aCancelledOrderHoldsNoStockSoEditingItMovesNone() {
        order.setOrderStatus("CANCELLED");
        OrderEditRequestDTO more = new OrderEditRequestDTO();
        more.setItems(List.of(line(teeLine.getId(), 9, null)));
        service.edit(ORDER, more, false, "A");
        assertEquals(9, teeLine.getQuantity());
        assertEquals(2, sizes.get(WHITE_M).getStockQuantity(), "untouched");
    }

    // ── adding and removing items ────────────────────────────────────────────

    @Test
    void anItemCanBeAddedAtTheShopsPriceTakingItsStock() {
        OrderEditRequestDTO edit = new OrderEditRequestDTO();
        edit.setItems(List.of(line(teeLine.getId(), 2, null), newLine(CAP, null, 3, null), newLine(TEE, WHITE_L, 1, null)));
        service.edit(ORDER, edit, false, "A");

        assertEquals(3, lines.size());
        OrderItem cap = lines.values().stream().filter(l -> l.getProductId() == CAP).findFirst().orElseThrow();
        assertEquals("Cap", cap.getProductName());
        assertEquals("CP-1", cap.getProductCode());
        assertEquals(new BigDecimal("400.00"), cap.getPriceAtOrder());
        assertEquals(2, plainStock.get(CAP), "3 of the 5 caps taken");
        OrderItem large = lines.values().stream().filter(l -> Long.valueOf(WHITE_L).equals(l.getVariantId())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("800.00"), large.getPriceAtOrder(), "a size with no price of its own costs the product's price");
        assertEquals(2, sizes.get(WHITE_L).getStockQuantity());
        assertEquals(new BigDecimal("3920.00"), order.getTotalAmount(), "1800 + 1200 + 800 + 120");
    }

    @Test
    void addingNeedsASizeWhereTheProductHasThemAndRefusesWhatIsAlreadyOnTheOrder() {
        OrderEditRequestDTO noSize = new OrderEditRequestDTO();
        noSize.setItems(List.of(line(teeLine.getId(), 2, null), newLine(TEE, null, 1, null)));
        assertTrue(assertThrows(InvalidRequestException.class, () -> service.edit(ORDER, noSize, false, "A")).getMessage().contains("Choose which size"));

        OrderEditRequestDTO again = new OrderEditRequestDTO();
        again.setItems(List.of(line(teeLine.getId(), 2, null), newLine(TEE, WHITE_M, 1, null)));
        assertTrue(assertThrows(InvalidRequestException.class, () -> service.edit(ORDER, again, false, "A")).getMessage().contains("already on this order"));

        OrderEditRequestDTO gone = new OrderEditRequestDTO();
        gone.setItems(List.of(line(teeLine.getId(), 2, null), newLine(99L, null, 1, null)));
        assertThrows(InvalidRequestException.class, () -> service.edit(ORDER, gone, false, "A"));
    }

    @Test
    void anItemLeftOutOfTheListIsRemovedAndItsStockReturned() {
        OrderEditRequestDTO add = new OrderEditRequestDTO();
        add.setItems(List.of(line(teeLine.getId(), 2, null), newLine(CAP, null, 1, null)));
        service.edit(ORDER, add, false, "A");
        UUID capLine = lines.values().stream().filter(l -> l.getProductId() == CAP).findFirst().orElseThrow().getId();

        OrderEditRequestDTO remove = new OrderEditRequestDTO();
        remove.setItems(List.of(line(capLine, 1, null)));
        List<String> changes = service.edit(ORDER, remove, false, "A");

        assertEquals(1, lines.size());
        assertFalse(lines.containsKey(teeLine.getId()));
        assertEquals(4, sizes.get(WHITE_M).getStockQuantity(), "the 2 tees went back");
        assertEquals(new BigDecimal("520.00"), order.getTotalAmount(), "400 + 120");
        assertTrue(changes.stream().anyMatch(c -> c.startsWith("Removed: Tee (M) × 2")), changes.toString());
    }

    @Test
    void anOrderCannotBeLeftWithNoItems() {
        OrderEditRequestDTO empty = new OrderEditRequestDTO();
        empty.setItems(List.of());
        assertTrue(assertThrows(InvalidRequestException.class, () -> service.edit(ORDER, empty, true, "A")).getMessage().contains("cancel it instead"));
    }

    // ── prices ───────────────────────────────────────────────────────────────

    @Test
    void pricesDeliveryAndDiscountCanBeChangedByWhoeverMayReprice() {
        OrderEditRequestDTO edit = new OrderEditRequestDTO();
        edit.setItems(List.of(line(teeLine.getId(), 2, "850")));
        edit.setShippingFee(new BigDecimal("60"));
        edit.setDiscountAmount(new BigDecimal("100"));
        List<String> changes = service.edit(ORDER, edit, true, "Ayesha");

        assertEquals(new BigDecimal("850.00"), teeLine.getPriceAtOrder());
        assertEquals(new BigDecimal("1700.00"), order.getSubtotalAmount());
        assertEquals(new BigDecimal("60.00"), order.getShippingFee());
        assertEquals(60.0, order.getDeliveryCharge());
        assertEquals(new BigDecimal("100.00"), order.getDiscountAmount());
        assertEquals(new BigDecimal("1660.00"), order.getTotalAmount(), "1700 − 100 + 60");
        assertTrue(changes.contains("Order total: ৳1920 → ৳1660"), changes.toString());
        assertEquals(2, sizes.get(WHITE_M).getStockQuantity(), "a price change moves no stock");
    }

    @Test
    void someoneWhoMayNotRepriceCanStillEditEverythingElse() {
        OrderEditRequestDTO price = new OrderEditRequestDTO();
        price.setItems(List.of(line(teeLine.getId(), 2, "1")));
        assertTrue(assertThrows(InvalidRequestException.class, () -> service.edit(ORDER, price, false, "A")).getMessage().contains("permission"));
        OrderEditRequestDTO fee = new OrderEditRequestDTO();
        fee.setShippingFee(BigDecimal.ZERO);
        assertThrows(InvalidRequestException.class, () -> service.edit(ORDER, fee, false, "A"));
        OrderEditRequestDTO cheapCap = new OrderEditRequestDTO();
        cheapCap.setItems(List.of(line(teeLine.getId(), 2, null), newLine(CAP, null, 1, "1")));
        assertThrows(InvalidRequestException.class, () -> service.edit(ORDER, cheapCap, false, "A"));
        assertEquals(new BigDecimal("900.00"), teeLine.getPriceAtOrder());
        assertEquals(new BigDecimal("1920.00"), order.getTotalAmount());
    }

    @Test
    void aDiscountCannotBeMoreThanTheItemsCostAndAnOldOneShrinksWithThem() {
        OrderEditRequestDTO tooMuch = new OrderEditRequestDTO();
        tooMuch.setDiscountAmount(new BigDecimal("5000"));
        assertThrows(InvalidRequestException.class, () -> service.edit(ORDER, tooMuch, true, "A"));

        order.setDiscountAmount(new BigDecimal("1500.00"));
        order.setTotalAmount(new BigDecimal("420.00"));
        OrderEditRequestDTO fewer = new OrderEditRequestDTO();
        fewer.setItems(List.of(line(teeLine.getId(), 1, null)));
        service.edit(ORDER, fewer, false, "A");
        assertEquals(new BigDecimal("900.00"), order.getDiscountAmount(), "it cannot take off more than the one tee left costs");
        assertEquals(new BigDecimal("120.00"), order.getTotalAmount(), "only delivery is left to pay");
    }

    @Test
    void whatTheTotalCarriedBesidesGoodsAndDeliveryIsKept() {
        // A made-to-order surcharge of ৳200 already in the total.
        order.setTotalAmount(new BigDecimal("2120.00"));
        OrderEditRequestDTO more = new OrderEditRequestDTO();
        more.setItems(List.of(line(teeLine.getId(), 3, null)));
        service.edit(ORDER, more, false, "A");
        assertEquals(new BigDecimal("3020.00"), order.getTotalAmount(), "3 × 900 + 120 + the 200 it already carried");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static OrderEditRequestDTO.Line line(UUID id, int quantity, String price) {
        OrderEditRequestDTO.Line line = new OrderEditRequestDTO.Line();
        line.setId(id);
        line.setQuantity(quantity);
        line.setUnitPrice(price == null ? null : new BigDecimal(price));
        return line;
    }

    private static OrderEditRequestDTO.Line newLine(long productId, Long variantId, int quantity, String price) {
        OrderEditRequestDTO.Line line = line(null, quantity, price);
        line.setProductId(productId);
        line.setVariantId(variantId);
        return line;
    }

    private static Product product(long id, String name, String code, String price) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setProductCode(code);
        p.setPrice(new BigDecimal(price));
        p.setDeleted(false);
        return p;
    }

    private static Variant variant(long id, long productId, String size, int stock, String price) {
        Variant v = new Variant();
        v.setId(id);
        v.setProductId(productId);
        v.setSize(size);
        v.setStockQuantity(stock);
        v.setPriceOverride(price == null ? null : new BigDecimal(price));
        v.setDeleted(false);
        return v;
    }
}
