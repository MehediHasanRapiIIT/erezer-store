package kn.org.deliverybackend.dto.response.cart;

import kn.org.deliverybackend.enumeration.StockStatus;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemDTO {

    private UUID cartItemId;
    private Long productId;
    private Long variantId;
    private String productName;
    private String imageUrl;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;   // computed: unitPrice × quantity
    private StockStatus stockStatus;
}