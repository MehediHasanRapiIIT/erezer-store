package kn.org.deliverybackend.dto.response.order;

import kn.org.deliverybackend.enumeration.OrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderHistoryItemDTO {
    private UUID orderId;
    private String storeName;
    private String storeImageUrl;
    private LocalDateTime orderDateTime;
    private BigDecimal totalAmount;
    private OrderStatus orderStatus;
    private int itemCount;
}
