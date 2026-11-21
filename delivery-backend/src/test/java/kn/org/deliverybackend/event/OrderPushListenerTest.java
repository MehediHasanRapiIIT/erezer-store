package kn.org.deliverybackend.event;

import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.PushDevice;
import kn.org.deliverybackend.enumeration.OrderStatus;
import kn.org.deliverybackend.repository.OrderRepository;
import kn.org.deliverybackend.repository.PushDeviceRepository;
import kn.org.deliverybackend.service.PushService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** App notifications go out at the customer-facing steps, to the ordering phone and the customer's phones. */
class OrderPushListenerTest {

    private final PushService push = mock(PushService.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final PushDeviceRepository devices = mock(PushDeviceRepository.class);
    private final OrderPushListener listener = new OrderPushListener(push, orders, devices);

    private Order order(UUID clientId, String pushToken) {
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setOrderNumber("EZ-123456");
        o.setClientId(clientId);
        o.setPushToken(pushToken);
        when(orders.findById(o.getId())).thenReturn(Optional.of(o));
        return o;
    }

    private OrderStatusChangedEvent changed(Order o, OrderStatus to, String courier) {
        return new OrderStatusChangedEvent(this, o.getId(), null, OrderStatus.PLACED, to, null, courier, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aShippedOrderReachesTheOrderingPhoneAndTheCustomersPhonesOnce() {
        UUID customer = UUID.randomUUID();
        Order o = order(customer, "phone-A");
        when(devices.findByUserId(customer)).thenReturn(List.of(new PushDevice("phone-A"), new PushDevice("phone-B")));

        listener.onOrderStatusChanged(changed(o, OrderStatus.SHIPPED, "Pathao"));

        ArgumentCaptor<Collection<String>> to = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Map<String, String>> data = ArgumentCaptor.forClass(Map.class);
        verify(push).send(to.capture(), eq("Order EZ-123456 has shipped"), eq("It is on its way with Pathao."), data.capture());
        assertEquals(List.of("phone-A", "phone-B"), List.copyOf(to.getValue()));
        assertEquals("false", data.getValue().get("guest"));
        assertEquals("EZ-123456", data.getValue().get("orderNumber"));
    }

    @Test
    void aGuestOrderReachesOnlyItsOwnPhone() {
        Order o = order(null, "guest-phone");
        listener.onOrderStatusChanged(changed(o, OrderStatus.DELIVERED, null));
        verify(push).send(eq(java.util.Set.of("guest-phone")), eq("Order EZ-123456 is delivered"), anyString(),
                eq(Map.of("orderId", o.getId().toString(), "orderNumber", "EZ-123456", "guest", "true")));
        verifyNoInteractions(devices);
    }

    @Test
    void stepsTheCustomerIsNotToldAboutSendNothing() {
        Order o = order(null, "guest-phone");
        listener.onOrderStatusChanged(changed(o, OrderStatus.PROCESSING, null));
        verifyNoInteractions(push);
    }

    @Test
    void anOrderFromTheWebsiteHasNoPhoneToTell() {
        Order o = order(null, null);
        listener.onOrderStatusChanged(changed(o, OrderStatus.ACCEPTED, null));
        verify(push, never()).send(any(), anyString(), anyString(), any());
    }
}
