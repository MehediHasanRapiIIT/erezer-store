package kn.org.deliverybackend.event;

import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.enumeration.OrderStatus;
import kn.org.deliverybackend.repository.OrderRepository;
import kn.org.deliverybackend.repository.UsersRepository;
import kn.org.deliverybackend.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Sends a short transactional SMS to the customer on the few order milestones
 * that matter most in BD (shipped / out-for-delivery / delivered). The phone is
 * looked up from the customer's account; guest orders (no account) are skipped.
 * Best-effort — {@link SmsService} never throws.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderSmsListener {

    private static final Set<OrderStatus> SMS_STATUSES = Set.of(
            OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED);

    private final SmsService smsService;
    private final OrderRepository orderRepository;
    private final UsersRepository usersRepository;

    @Value("${app.frontend.store-url}")
    private String storeUrl;

    @EventListener
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        OrderStatus to = event.getToStatus() == null ? null : event.getToStatus().normalize();
        if (to == null || !SMS_STATUSES.contains(to)) {
            return;
        }
        Order order = orderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null) {
            return;
        }
        // Account holders by their profile number; guests by the phone they gave at checkout.
        String phone = order.getClientId() != null
                ? usersRepository.findById(order.getClientId()).map(u -> u.getPhoneNumber()).orElse(order.getCustomerPhone())
                : order.getCustomerPhone();
        if (phone == null || phone.isBlank()) {
            return;
        }
        smsService.send(phone, messageFor(to, event, order));
    }

    private String messageFor(OrderStatus to, OrderStatusChangedEvent event, Order order) {
        String shortId = order.getOrderNumber() != null ? order.getOrderNumber()
                : event.getOrderId().toString().substring(0, 8);
        String trackUrl = order.getClientId() == null && order.getOrderNumber() != null
                ? storeUrl + "/track-order?number=" + order.getOrderNumber()
                : storeUrl + "/orders/" + event.getOrderId();
        return switch (to) {
            case SHIPPED -> "Erezer: your order " + shortId + " has shipped"
                    + (event.getTrackingNumber() != null && !event.getTrackingNumber().isBlank()
                        ? " (tracking " + event.getTrackingNumber() + ")" : "")
                    + ". Track: " + trackUrl;
            case OUT_FOR_DELIVERY -> "Erezer: your order " + shortId
                    + " is out for delivery today. Please keep your phone reachable.";
            case DELIVERED -> "Erezer: your order " + shortId
                    + " has been delivered. Thank you for shopping with us!";
            default -> "Erezer: update on your order " + shortId + ".";
        };
    }
}
