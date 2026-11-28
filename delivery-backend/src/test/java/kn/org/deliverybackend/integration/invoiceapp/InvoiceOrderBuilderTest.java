package kn.org.deliverybackend.integration.invoiceapp;

import kn.org.deliverybackend.dto.variant.ProductOptionDTO;
import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.OrderItem;
import kn.org.deliverybackend.entity.Payment;
import kn.org.deliverybackend.entity.PaymentRefund;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.Variant;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.ShopItem;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.ShopOrder;
import kn.org.deliverybackend.repository.OrderItemRepository;
import kn.org.deliverybackend.repository.PaymentRefundRepository;
import kn.org.deliverybackend.repository.PaymentRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.VariantRepository;
import kn.org.deliverybackend.service.FileStorageService;
import kn.org.deliverybackend.service.FileStorageService.StoredFile;
import kn.org.deliverybackend.service.impl.OrderLinePicture;
import kn.org.deliverybackend.service.impl.ProductOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A shop order as Erezer Invoice receives it. */
class InvoiceOrderBuilderTest {

    private static final String BLACK_URL = "http://localhost:9000/products/black.jpg";

    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final VariantRepository variants = mock(VariantRepository.class);
    private final OrderLinePicture pictures = mock(OrderLinePicture.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final InvoiceOrderBuilder builder =
            new InvoiceOrderBuilder(items, payments, refunds, products, variants, pictures, storage);

    private final Order order = new Order();
    private final OrderItem tee = new OrderItem();
    private final StoredFile blackPhoto = new StoredFile("black.jpg", "image/jpeg", new byte[] {1, 2, 3});

    @BeforeEach
    void anOrder() {
        order.setId(UUID.randomUUID());
        order.setOrderNumber("EZ-482915");
        order.setCreatedAt(new Date(1_790_000_000_000L));
        order.setCustomerName("Rahim Uddin");
        order.setCustomerPhone("+8801712345678");
        order.setCustomerEmail("rahim@example.com");
        order.setDeliveryAddress("House 1, Road 2, Dhanmondi");
        order.setPaymentMethod("BKASH");
        order.setShippingFee(new BigDecimal("60.00"));
        order.setDiscountAmount(new BigDecimal("50.00"));

        ProductOptionDTO colour = new ProductOptionDTO("o1", "Colour", "COLOUR", List.of(
                new ProductOptionDTO.Value("v1", "Black", "#000000"),
                new ProductOptionDTO.Value("v2", "White", "#ffffff")));
        Product product = new Product();
        product.setId(7L);
        product.setName("Drop Shoulder Tee");
        product.setImageUrl("http://localhost:9000/products/main.jpg");
        product.setOptionsJson(ProductOptions.write(List.of(colour)));
        Variant variant = new Variant();
        variant.setId(70L);
        variant.setSize("L");
        variant.setFit("DROP_SHOULDER");
        variant.setOptionKey(ProductOptions.key(Map.of("o1", "v1")));

        tee.setOrderId(order.getId());
        tee.setProductId(7L);
        tee.setVariantId(70L);
        tee.setProductName("Drop Shoulder Tee");
        tee.setProductCode("TS-00012");
        tee.setVariantName("Black / Drop Shoulder / L");
        tee.setQuantity(2);
        tee.setPriceAtOrder(new BigDecimal("500"));

        when(items.findByOrderId(order.getId())).thenReturn(List.of(tee));
        when(products.findById(7L)).thenReturn(Optional.of(product));
        when(variants.findById(70L)).thenReturn(Optional.of(variant));
        when(pictures.ofChoice(tee)).thenReturn(BLACK_URL);
        when(storage.read(eq(BLACK_URL), anyLong())).thenReturn(Optional.of(blackPhoto));
    }

    @Test
    void aPaidBkashOrderGoesWithItsColourSizeAndPhoto() {
        when(payments.findByOrderId(order.getId())).thenReturn(List.of(payment("Failed"), payment("Completed")));

        InvoiceOrderBuilder.Prepared prepared = builder.build(order);
        ShopOrder sent = prepared.order();

        assertEquals("EZ-482915", sent.orderNumber());
        assertEquals("BKASH", sent.paymentMethod());
        assertEquals(new BigDecimal("1010.00"), sent.advance(), "only the payment that went through counts");
        assertEquals(new BigDecimal("60.00"), sent.deliveryCharge());
        assertEquals(new BigDecimal("50.00"), sent.discount());
        assertEquals("+8801712345678", sent.customer().phone());
        assertEquals("rahim@example.com", sent.customer().email());

        ShopItem line = sent.items().get(0);
        assertEquals("Drop Shoulder Tee", line.productName());
        assertEquals("TS-00012", line.productCode());
        assertEquals("Black", line.color());
        assertEquals("L", line.size());
        assertEquals(new BigDecimal("500.00"), line.unitPrice());
        assertEquals(2, line.quantity());
        assertEquals("Black / Drop Shoulder / L", line.description());
        assertEquals("Black · Drop Shoulder · L", line.imageDescription());

        assertEquals(List.of(List.of(blackPhoto)), prepared.photos());
        assertEquals(0, prepared.missingPhotos());
    }

    @Test
    void moneyRefundedByBkashIsNotCountedAsPaid() {
        when(payments.findByOrderId(order.getId())).thenReturn(List.of(payment("Completed")));
        when(refunds.findByOrderIdOrderByCreatedAtAsc(order.getId())).thenReturn(List.of(
                refund(PaymentRefund.COMPLETED, "100.00"), refund("FAILED", "300.00")));

        ShopOrder sent = builder.build(order).order();
        assertEquals("BKASH", sent.paymentMethod());
        assertEquals(new BigDecimal("910.00"), sent.advance(), "1010 paid, 100 handed back; the failed refund doesn't count");
    }

    @Test
    void anUnpaidOrderIsCollectedOnDelivery() {
        when(payments.findByOrderId(order.getId())).thenReturn(List.of(payment("Failed")));

        ShopOrder sent = builder.build(order).order();
        assertEquals("CASH", sent.paymentMethod());
        assertEquals(0, sent.advance().signum());
    }

    @Test
    void aCustomSizeChargeIsItsOwnLineAndAMissingPhotoIsCounted() {
        tee.setCustomSurcharge(new BigDecimal("150"));
        tee.setCustomMeasurements("{\"Chest\":38,\"Length\":40,\"comments\":\"loose fit\"}");
        when(storage.read(eq(BLACK_URL), anyLong())).thenReturn(Optional.empty());

        InvoiceOrderBuilder.Prepared prepared = builder.build(order);
        List<ShopItem> lines = prepared.order().items();

        assertEquals(2, lines.size());
        assertEquals("Black / Drop Shoulder / L\nCustom size: Chest 38, Length 40, loose fit", lines.get(0).description());
        assertEquals("Custom size charge: Drop Shoulder Tee", lines.get(1).productName());
        assertEquals(new BigDecimal("150.00"), lines.get(1).unitPrice());
        assertEquals(1, lines.get(1).quantity());
        assertEquals(2, prepared.photos().size(), "photos stay lined up with the lines");
        assertTrue(prepared.photos().get(1).isEmpty());
        assertEquals(1, prepared.missingPhotos());
    }

    @Test
    void aLineWhoseVariantIsGoneStillGetsItsSize() {
        when(variants.findById(70L)).thenReturn(Optional.empty());
        ShopItem line = builder.build(order).order().items().get(0);
        assertEquals("L", line.size());
        assertNull(line.color());
        assertNull(InvoiceOrderBuilder.size(null, "Black / Drop Shoulder"), "a fit isn't a size");
    }

    @Test
    void anOrderWithoutAPhoneCantBeSent() {
        order.setCustomerPhone("  ");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> builder.build(order));
        assertEquals("The order has no phone number.", e.getMessage());
    }

    @Test
    void aBadEmailIsLeftOutRatherThanRefused() {
        order.setCustomerEmail("not an email");
        assertNull(builder.build(order).order().customer().email());
    }

    private static PaymentRefund refund(String status, String amount) {
        PaymentRefund r = new PaymentRefund();
        r.setStatus(status);
        r.setAmount(new BigDecimal(amount));
        return r;
    }

    private static Payment payment(String status) {
        Payment p = new Payment();
        p.setStatus(status);
        p.setAmount(new BigDecimal("1010.00"));
        return p;
    }
}
