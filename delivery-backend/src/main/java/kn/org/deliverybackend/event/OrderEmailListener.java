package kn.org.deliverybackend.event;

import kn.org.deliverybackend.util.Taka;
import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.OrderItem;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.entity.StoreSettings;
import kn.org.deliverybackend.enumeration.OrderStatus;
import kn.org.deliverybackend.repository.OrderItemRepository;
import kn.org.deliverybackend.repository.OrderRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.StoreSettingsRepository;
import kn.org.deliverybackend.service.EmailService;
import kn.org.deliverybackend.service.impl.CustomerEmailSettingsService;
import kn.org.deliverybackend.service.impl.OrderServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The emails a customer gets about an order. To keep within the mailbox's daily
 * limit, and so mail services see a steady, expected flow from the shop, there
 * are only four, at the moments that matter:
 * <ul>
 *   <li><b>Accepted</b>: the order is confirmed, with its items and total;</li>
 *   <li><b>Shipped</b>: the courier, the tracking number and the tracking link;</li>
 *   <li><b>Delivered</b>: thanks, and where to leave a review (the website,
 *       Facebook, Instagram, WhatsApp; set on the Settings page);</li>
 *   <li><b>Cancelled</b>: so the customer is never left waiting.</li>
 * </ul>
 * Placing an order sends no email (an SMS instead, see {@link OrderSmsListener}),
 * and the steps in between (Processing, Out for delivery, Returned) send none.
 * The email itself goes out in the background, so a slow mail server never
 * holds up the admin's status change.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderEmailListener {

    /** The steps that send the customer an email. */
    static final Set<OrderStatus> EMAIL_STATUSES = Set.of(
            OrderStatus.ACCEPTED, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.CANCELLED);

    private final EmailService emailService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final StoreSettingsRepository storeSettings;
    private final CustomerEmailSettingsService customerEmails;

    @Value("${app.frontend.store-url}")
    private String storeUrl;

    @EventListener
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        OrderStatus to = event.getToStatus() == null ? null : event.getToStatus().normalize();
        if (to == null || !EMAIL_STATUSES.contains(to) || isBlank(event.getCustomerEmail())) {
            return;
        }
        Order order = orderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null) {
            log.warn("Status email skipped: order {} not found", event.getOrderId());
            return;
        }
        String number = order.getOrderNumber() != null ? order.getOrderNumber() : order.getId().toString().substring(0, 8);

        Map<String, Object> vars = new HashMap<>();
        vars.put("orderNumber", number);
        vars.put("customerName", firstName(order.getCustomerName()));
        vars.put("orderUrl", orderUrl(order));
        vars.put("shopUrl", storeUrl);
        vars.put("note", blankToNull(event.getNote()));

        String subject;
        String template;
        switch (to) {
            case ACCEPTED -> {
                subject = "Your Erezer order " + number + " is confirmed";
                template = "order-accepted";
                putItemsAndTotals(vars, order);
                vars.put("deliveryAddress", blankToNull(order.getDeliveryAddress()));
                vars.put("paymentLabel", paymentLabel(order.getPaymentMethod()));
            }
            case SHIPPED -> {
                subject = "Your Erezer order " + number + " has shipped";
                template = "order-shipped";
                vars.put("courier", blankToNull(event.getCourierName() != null ? event.getCourierName() : order.getCourierName()));
                vars.put("trackingNumber", blankToNull(event.getTrackingNumber() != null ? event.getTrackingNumber() : order.getTrackingNumber()));
                vars.put("trackingUrl", blankToNull(order.getTrackingUrl()));
                vars.put("codAmount", isCashOnDelivery(order.getPaymentMethod()) ? Taka.format(orZero(order.getTotalAmount())) : null);
            }
            case DELIVERED -> {
                subject = "Your Erezer order " + number + " has been delivered";
                template = "order-delivered";
                putReviewOptions(vars, order);
                StoreSettings shop = storeSettings.findById(StoreSettings.SINGLETON_ID).orElse(null);
                Integer days = shop == null ? null : shop.getExchangeWindowDays();
                vars.put("returnDays", days != null && days > 0 ? days : null);
            }
            default -> {
                subject = "Your Erezer order " + number + " has been cancelled";
                template = "order-cancelled";
                vars.put("byCustomer", event.getSource() instanceof OrderServiceImpl);
                vars.put("paidOnline", !isCashOnDelivery(order.getPaymentMethod()));
            }
        }
        emailService.send(event.getCustomerEmail(), subject, template, vars);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /** Account orders open in the customer's order history; guest orders on the Track Order page. */
    private String orderUrl(Order order) {
        if (order.getClientId() == null && order.getOrderNumber() != null) {
            return storeUrl + "/track-order?number=" + order.getOrderNumber();
        }
        return storeUrl + "/orders/" + order.getId();
    }

    private void putItemsAndTotals(Map<String, Object> vars, Order order) {
        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItem oi : orderItemRepository.findByOrderId(order.getId())) {
            BigDecimal price = orZero(oi.getPriceAtOrder());
            int qty = oi.getQuantity() == null ? 0 : oi.getQuantity();
            BigDecimal line = price.multiply(BigDecimal.valueOf(qty));
            subtotal = subtotal.add(line);
            Map<String, Object> row = new HashMap<>();
            row.put("name", productName(oi));
            // The colour, fit and size that were chosen, so the email says which one was ordered.
            row.put("variant", blankToNull(oi.getVariantName() != null ? oi.getVariantName() : oi.getVariantSize()));
            row.put("quantity", qty);
            row.put("lineTotal", Taka.format(line));
            items.add(row);
        }
        BigDecimal shipping = order.getShippingFee() != null ? order.getShippingFee()
                : order.getDeliveryCharge() != null ? BigDecimal.valueOf(order.getDeliveryCharge()) : BigDecimal.ZERO;
        BigDecimal discount = orZero(order.getDiscountAmount());
        vars.put("items", items);
        vars.put("subtotal", Taka.format(order.getSubtotalAmount() != null ? order.getSubtotalAmount() : subtotal));
        vars.put("discount", discount.signum() > 0 ? "− " + Taka.format(discount) : null);
        vars.put("shipping", Taka.format(shipping));
        vars.put("total", Taka.format(order.getTotalAmount() != null ? order.getTotalAmount() : subtotal.add(shipping).subtract(discount)));
    }

    /** Where the "delivered" email invites a review: each product's page, and the shop's message channels. */
    private void putReviewOptions(Map<String, Object> vars, Order order) {
        CustomerEmailSettingsService.Settings s = customerEmails.get();
        List<Map<String, Object>> products = new ArrayList<>();
        if (s.reviewOnWebsite()) {
            Map<Long, String> seen = new LinkedHashMap<>();
            for (OrderItem oi : orderItemRepository.findByOrderId(order.getId())) {
                if (oi.getProductId() != null && productRepository.existsById(oi.getProductId())) {
                    seen.putIfAbsent(oi.getProductId(), productName(oi));
                }
            }
            seen.forEach((id, name) -> products.add(Map.of("name", name, "url", storeUrl + "/product/" + id + "#reviews")));
        }
        String facebook = blankToNull(s.facebookUrl());
        String instagram = blankToNull(s.instagramUrl());
        String whatsapp = CustomerEmailSettingsService.whatsappLink(s.whatsappNumber());
        vars.put("reviewProducts", products.isEmpty() ? null : products);
        vars.put("facebookUrl", facebook);
        vars.put("instagramUrl", instagram);
        vars.put("whatsappUrl", whatsapp);
        vars.put("hasReviewOptions", !products.isEmpty() || facebook != null || instagram != null || whatsapp != null);
    }

    private String productName(OrderItem oi) {
        return productRepository.findById(oi.getProductId())
                .map(Product::getName)
                // A product deleted since: the name kept on the order line.
                .orElse(oi.getProductName() != null && !oi.getProductName().isBlank() ? oi.getProductName() : "Item");
    }

    static String paymentLabel(String method) {
        if (method == null) return "—";
        return switch (method.trim().toUpperCase(Locale.ROOT)) {
            case "CASH", "COD" -> "Cash on delivery";
            case "BKASH" -> "bKash";
            case "CARD" -> "Card";
            default -> method;
        };
    }

    private static boolean isCashOnDelivery(String method) {
        return method == null || "CASH".equalsIgnoreCase(method.trim()) || "COD".equalsIgnoreCase(method.trim());
    }

    /** "Mehedi Hasan" → "Mehedi": a greeting by first name. */
    static String firstName(String name) {
        if (name == null || name.isBlank()) return "there";
        return name.trim().split("\\s+")[0];
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }

    private static String blankToNull(String v) {
        return isBlank(v) ? null : v.trim();
    }
}
