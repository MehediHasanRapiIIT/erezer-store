package kn.org.deliverybackend.event;

import jakarta.annotation.Nullable;
import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.OrderItem;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.enumeration.OrderStatus;
import kn.org.deliverybackend.repository.OrderItemRepository;
import kn.org.deliverybackend.repository.OrderRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sends order-related emails asynchronously after the publishing transaction
 * commits — so we never email a customer about a row that was rolled back.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderEmailListener {

    private final EmailService emailService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    @Value("${app.frontend.store-url}")
    private String storeUrl;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        if (event.getCustomerEmail() == null) {
            return;
        }
        Order order = orderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null) {
            log.warn("OrderPlacedEvent fired for missing order {}", event.getOrderId());
            return;
        }
        Map<String, Object> vars = buildOrderPlacedVars(order);
        emailService.send(event.getCustomerEmail(),
                "Your Erezer order is confirmed",
                "order-placed",
                vars);
    }

    @EventListener
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        if (event.getCustomerEmail() == null) {
            return;
        }
        OrderStatus to = event.getToStatus();
        Map<String, Object> vars = new HashMap<>();
        vars.put("orderId", event.getOrderId());
        vars.put("statusLabel", humanLabel(to));
        vars.put("headline", headlineFor(to));
        vars.put("statusMessage", messageFor(to, event.getNote()));
        vars.put("orderUrl", storeUrl + "/orders/" + event.getOrderId());
        vars.put("courier", emptyToNull(event.getCourierName()));
        vars.put("trackingNumber", emptyToNull(event.getTrackingNumber()));

        emailService.send(event.getCustomerEmail(),
                "Update on your Erezer order",
                "order-status-changed",
                vars);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private Map<String, Object> buildOrderPlacedVars(Order order) {
        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.US);

        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItem oi : orderItemRepository.findByOrderId(order.getId())) {
            String name = productRepository.findById(oi.getProductId())
                    .map(Product::getName)
                    .orElse("Item");
            BigDecimal price = oi.getPriceAtOrder() != null ? oi.getPriceAtOrder() : BigDecimal.ZERO;
            BigDecimal line = price.multiply(BigDecimal.valueOf(oi.getQuantity()));
            subtotal = subtotal.add(line);

            Map<String, Object> row = new HashMap<>();
            row.put("name", name);
            row.put("quantity", oi.getQuantity());
            row.put("lineTotal", currency.format(line));
            items.add(row);
        }

        BigDecimal shipping = order.getShippingFee() != null
                ? order.getShippingFee()
                : (order.getDeliveryCharge() != null
                        ? BigDecimal.valueOf(order.getDeliveryCharge())
                        : BigDecimal.ZERO);

        Map<String, Object> vars = new HashMap<>();
        vars.put("orderId", order.getId());
        vars.put("placedAt", order.getCreatedAt() != null
                ? DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mma")
                        .format(order.getCreatedAt().toInstant()
                                .atZone(java.time.ZoneId.systemDefault()))
                : "just now");
        vars.put("items", items);
        vars.put("subtotal", currency.format(subtotal));
        vars.put("shipping", currency.format(shipping));
        vars.put("total", currency.format(
                order.getTotalAmount() != null ? order.getTotalAmount() : subtotal.add(shipping)));
        vars.put("orderUrl", storeUrl + "/orders/" + order.getId());
        return vars;
    }

    private String humanLabel(OrderStatus s) {
        return switch (s.normalize()) {
            case PLACED            -> "Placed";
            case ACCEPTED          -> "Accepted";
            case IN_PRODUCTION     -> "In production";
            case PROCESSING        -> "Processing";
            case SHIPPED           -> "Shipped";
            case OUT_FOR_DELIVERY  -> "Out for delivery";
            case DELIVERED         -> "Delivered";
            case CANCELLED         -> "Cancelled";
            case RETURNED          -> "Returned";
            default                -> s.name();
        };
    }

    private String headlineFor(OrderStatus s) {
        return switch (s.normalize()) {
            case ACCEPTED          -> "We've accepted your order";
            case IN_PRODUCTION     -> "Your order is being made";
            case PROCESSING        -> "Your order is being prepared";
            case SHIPPED           -> "Your order has shipped";
            case OUT_FOR_DELIVERY  -> "Out for delivery today";
            case DELIVERED         -> "Your order is delivered";
            case CANCELLED         -> "Your order was cancelled";
            case RETURNED          -> "Your return is recorded";
            default                -> "Your order has been updated";
        };
    }

    private @Nullable String messageFor(OrderStatus s, @Nullable String note) {
        if (note != null && !note.isBlank()) {
            return note.trim();
        }
        return switch (s.normalize()) {
            case ACCEPTED         -> "Thanks for shopping with us. We're getting your items ready.";
            case IN_PRODUCTION    -> "Your pieces are being crafted. We'll let you know the moment they're ready.";
            case PROCESSING       -> "We're packing your order with care.";
            case SHIPPED          -> "Your order is on its way. Tracking details are below.";
            case OUT_FOR_DELIVERY -> "Your order is out for delivery and should arrive soon.";
            case DELIVERED        -> "Enjoy your new pieces! We'd love to hear what you think — leave a review on your dashboard.";
            case CANCELLED        -> "Your order has been cancelled. Any payment will be refunded according to our policy.";
            case RETURNED         -> "Your return has been recorded. We'll be in touch with refund details shortly.";
            default               -> null;
        };
    }

    private @Nullable String emptyToNull(@Nullable String v) {
        return (v == null || v.isBlank()) ? null : v;
    }
}
