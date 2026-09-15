package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.OrderDTO;
import kn.org.deliverybackend.dto.OrderItemDTO;
import kn.org.deliverybackend.dto.order.OrderTrackingDTO;
import kn.org.deliverybackend.dto.order.PublicOrderTrackingDTO;
import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.OrderRepository;
import kn.org.deliverybackend.util.OrderNumbers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** Tracking by order number, for anyone - so it shows progress and items only. */
@Service
@RequiredArgsConstructor
public class PublicOrderTrackingService {

    /** One message for an unknown number and a badly typed one, so nothing is learned from the difference. */
    public static final String NOT_FOUND = "We couldn't find an order with that number.";

    private final OrderRepository orderRepository;
    private final OrderHistoryService orderHistoryService;
    private final OrderService orderService;

    @Transactional(readOnly = true)
    public PublicOrderTrackingDTO track(String typedNumber) {
        String number = OrderNumbers.normalize(typedNumber)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        Order order = orderRepository.findByOrderNumberAndDeletedFalse(number)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));

        OrderTrackingDTO tracking = orderService.getOrderTracking(order.getId());
        OrderDTO withItems = orderHistoryService.getOrderByIdForAdmin(order.getId());

        List<PublicOrderTrackingDTO.Step> steps = tracking.getHistory() == null ? List.of()
                : tracking.getHistory().stream()
                        .map(h -> new PublicOrderTrackingDTO.Step(h.getToStatus(), h.getCreatedAt()))
                        .toList();
        List<PublicOrderTrackingDTO.Item> items = withItems.getOrderItems() == null ? List.of()
                : withItems.getOrderItems().stream().map(PublicOrderTrackingService::item).toList();

        return new PublicOrderTrackingDTO(
                order.getOrderNumber(),
                tracking.getCurrentStatus(),
                placedAt(order),
                steps,
                order.getCourierName(),
                order.getTrackingNumber(),
                items,
                order.getSubtotalAmount(),
                order.getDiscountAmount(),
                order.getShippingFee() != null ? order.getShippingFee()
                        : order.getDeliveryCharge() != null ? BigDecimal.valueOf(order.getDeliveryCharge()) : null,
                order.getTotalAmount(),
                order.getPaymentMethod());
    }

    private static PublicOrderTrackingDTO.Item item(OrderItemDTO i) {
        BigDecimal unit = i.getPriceAtOrder() == null ? BigDecimal.ZERO : i.getPriceAtOrder();
        int qty = i.getQuantity() == null ? 0 : i.getQuantity();
        String size = i.getVariantSize() != null && !i.getVariantSize().isBlank() ? i.getVariantSize() : i.getVariantName();
        return new PublicOrderTrackingDTO.Item(i.getProductName(), i.getImageUrl(), size, qty,
                unit, unit.multiply(BigDecimal.valueOf(qty)));
    }

    private static LocalDateTime placedAt(Order order) {
        return order.getCreatedAt() == null ? null
                : LocalDateTime.ofInstant(order.getCreatedAt().toInstant(), ZoneId.systemDefault());
    }
}
