package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.entity.CustomerEmailSettings;
import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.enumeration.OrderStatus;
import kn.org.deliverybackend.event.OrderEmailListener;
import kn.org.deliverybackend.event.OrderStatusChangedEvent;
import kn.org.deliverybackend.repository.CustomerEmailSettingsRepository;
import kn.org.deliverybackend.repository.OrderItemRepository;
import kn.org.deliverybackend.repository.OrderRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.StoreSettingsRepository;
import kn.org.deliverybackend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Only four steps email the customer: Accepted, Shipped, Delivered and Cancelled. */
class OrderEmailsTest {

    private final EmailService email = mock(EmailService.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final CustomerEmailSettingsRepository settingsRepo = mock(CustomerEmailSettingsRepository.class);
    private final OrderEmailListener listener = new OrderEmailListener(email, orders, items, mock(ProductRepository.class),
            mock(StoreSettingsRepository.class), new CustomerEmailSettingsService(settingsRepo));
    private final Order order = new Order();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(listener, "storeUrl", "https://erezer.com");
        order.setId(UUID.randomUUID());
        order.setOrderNumber("EZ-583882");
        order.setCustomerName("Mehedi Hasan");
        order.setPaymentMethod("CASH");
        order.setTotalAmount(new BigDecimal("960"));
        order.setTrackingUrl("https://steadfast.com.bd/t/123");
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(items.findByOrderId(any())).thenReturn(List.of());
        CustomerEmailSettings s = new CustomerEmailSettings();
        s.setFacebookUrl("https://m.me/erezer");
        s.setWhatsappNumber("01712-345678");
        when(settingsRepo.findById(CustomerEmailSettings.SINGLETON_ID)).thenReturn(Optional.of(s));
    }

    private void changeTo(OrderStatus to) {
        listener.onOrderStatusChanged(new OrderStatusChangedEvent(this, order.getId(), "c@example.com",
                OrderStatus.PLACED, to, null, "Steadfast", "123"));
    }

    @Test
    void theStepsInBetweenSendNothing() {
        for (OrderStatus s : List.of(OrderStatus.PLACED, OrderStatus.IN_PRODUCTION, OrderStatus.PROCESSING,
                OrderStatus.OUT_FOR_DELIVERY, OrderStatus.RETURNED)) {
            changeTo(s);
        }
        verify(email, never()).send(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shippedNamesTheCourierTheNumberAndTheLink() {
        changeTo(OrderStatus.SHIPPED);
        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(email).send(eq("c@example.com"), eq("Your Erezer order EZ-583882 has shipped"), eq("order-shipped"), vars.capture());
        assertEquals("Steadfast", vars.getValue().get("courier"));
        assertEquals("123", vars.getValue().get("trackingNumber"));
        assertEquals("https://steadfast.com.bd/t/123", vars.getValue().get("trackingUrl"));
        assertEquals("Mehedi", vars.getValue().get("customerName"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deliveredInvitesAReviewWhereTheAdminChose() {
        changeTo(OrderStatus.DELIVERED);
        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(email).send(eq("c@example.com"), eq("Your Erezer order EZ-583882 has been delivered"), eq("order-delivered"), vars.capture());
        assertEquals("https://m.me/erezer", vars.getValue().get("facebookUrl"));
        assertEquals("https://wa.me/8801712345678", vars.getValue().get("whatsappUrl"));
        assertEquals(null, vars.getValue().get("instagramUrl"));
        assertTrue((Boolean) vars.getValue().get("hasReviewOptions"));
    }

    @Test
    void acceptedAndCancelledEachSendOne() {
        changeTo(OrderStatus.ACCEPTED);
        changeTo(OrderStatus.CANCELLED);
        verify(email).send(eq("c@example.com"), eq("Your Erezer order EZ-583882 is confirmed"), eq("order-accepted"), anyMap());
        verify(email).send(eq("c@example.com"), eq("Your Erezer order EZ-583882 has been cancelled"), eq("order-cancelled"), anyMap());
    }
}
