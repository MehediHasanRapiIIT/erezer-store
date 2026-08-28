package kn.org.deliverybackend.dto.response.order;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDetailItemDTO {
    private UUID itemId;
    private Long productId;
    private String productName;
    private String productImageUrl;
    private Integer quantity;
    private BigDecimal priceAtOrder;
    private Long variantId;
    private String variantName;
    private String variantSize;
}
