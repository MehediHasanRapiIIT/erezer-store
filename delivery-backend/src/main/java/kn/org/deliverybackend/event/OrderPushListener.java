package kn.org.deliverybackend.event;

import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.PushDevice;
import kn.org.deliverybackend.enumeration.OrderStatus;
import kn.org.deliverybackend.repository.OrderRepository;
import kn.org.deliverybackend.repository.PushDeviceRepository;
import kn.org.deliverybackend.service.PushService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A notification on the customer's phone (the Erezer app) at the same steps the
 * customer gets an email: accepted, shipped, out for delivery, delivered,
 * cancelled. It goes to the phone that placed the order and to every phone the
 * customer is logged in on. Tapping it opens the order in the app.
 */
@Component
@RequiredArgsConstructor
public class OrderPushListener {

    private static final Set<OrderStatus> PUSH_STATUSES = Set.of(OrderStatus.ACCEPTED, OrderStatus.SHIPPED,
            OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, OrderStatus.CANCELLED);

    private final PushService pushService;
    private final OrderRepository orderRepository;
    private final PushDeviceRepository devices;

    /** After the change is saved, so a rolled-back change never notifies. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        OrderStatus to = event.getToStatus() == null ? null : event.getToStatus().normalize();
        if (to == null || !PUSH_STATUSES.contains(to)) return;
        Order order = orderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null) return;

        Set<String> phones = new LinkedHashSet<>();
        if (order.getPushToken() != null) phones.add(order.getPushToken());
        if (order.getClientId() != null) {
            devices.findByUserId(order.getClientId()).stream().map(PushDevice::getToken).forEach(phones::add);
        }
        if (phones.isEmpty()) return;

        String number = order.getOrderNumber() != null ? order.getOrderNumber() : order.getId().toString().substring(0, 8);
        pushService.send(phones, titleFor(to, number), bodyFor(to, event), Map.of(
                "orderId", order.getId().toString(),
                "orderNumber", number,
                // A guest's order opens on Track an order; an account holder's in My orders.
                "guest", String.valueOf(order.getClientId() == null)));
    }

    static String titleFor(OrderStatus to, String number) {
        return switch (to) {
            case ACCEPTED -> "Order " + number + " is confirmed";
            case SHIPPED -> "Order " + number + " has shipped";
            case OUT_FOR_DELIVERY -> "Order " + number + " is out for delivery";
            case DELIVERED -> "Order " + number + " is delivered";
            case CANCELLED -> "Order " + number + " is cancelled";
            default -> "Update on order " + number;
        };
    }

    static String bodyFor(OrderStatus to, OrderStatusChangedEvent event) {
        return switch (to) {
            case ACCEPTED -> "We are getting your order ready.";
            case SHIPPED -> event.getCourierName() != null && !event.getCourierName().isBlank()
                    ? "It is on its way with " + event.getCourierName().trim() + "."
                    : "It is on its way to you.";
            case OUT_FOR_DELIVERY -> "It arrives today. Please keep your phone nearby.";
            case DELIVERED -> "Thank you for shopping with Erezer!";
            case CANCELLED -> "Tap to see the details.";
            default -> "Tap to see your order.";
        };
    }
}
