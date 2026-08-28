package kn.org.deliverybackend.dto.response.order;

import kn.org.deliverybackend.enumeration.OrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDetailDTO {
    private UUID orderId;
    private String storeName;
    private String storeImageUrl;
    private LocalDateTime orderDateTime;
    private BigDecimal totalAmount;
    private BigDecimal deliveryCharge;
    private OrderStatus orderStatus;
    private String paymentMethod;
    private String deliveryAddress;
    private int itemCount;
    private List<OrderDetailItemDTO> items;
}
