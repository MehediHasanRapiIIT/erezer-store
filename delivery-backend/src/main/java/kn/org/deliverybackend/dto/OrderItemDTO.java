package kn.org.deliverybackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDTO {

    private UUID id;

    private UUID orderId;

    private Long productId;

    private String productName;

    private String imageUrl;

    private Integer quantity;

    private BigDecimal priceAtOrder;

    private Long variantId;

    private String variantName;

    private String variantSize;

    // Custom (made-to-order) sizing — measurements JSON + applied surcharge.
    private String customMeasurements;

    private BigDecimal customSurcharge;
}
