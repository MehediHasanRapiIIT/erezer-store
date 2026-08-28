package kn.org.deliverybackend.dto.returns;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnItemDTO {
    private UUID id;
    private UUID orderItemId;
    private Long productId;
    private String productName;
    private Integer quantity;
    private String condition;
    private BigDecimal lineRefundAmount;
}
