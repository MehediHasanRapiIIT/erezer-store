package kn.org.deliverybackend.dto.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * What anyone with an order number may see: progress and what was ordered.
 * Deliberately nothing about the person - no name, phone, email or address,
 * and no staff notes or who changed a status.
 */
public record PublicOrderTrackingDTO(String orderNumber,
                                     String status,
                                     LocalDateTime placedAt,
                                     List<Step> steps,
                                     String courierName,
                                     String courierTrackingNumber,
                                     List<Item> items,
                                     BigDecimal subtotal,
                                     BigDecimal discount,
                                     BigDecimal shipping,
                                     BigDecimal total,
                                     String paymentMethod) {

    public record Step(String status, LocalDateTime at) {
    }

    public record Item(String name, String imageUrl, String size, Integer quantity,
                       BigDecimal unitPrice, BigDecimal lineTotal) {
    }
}
