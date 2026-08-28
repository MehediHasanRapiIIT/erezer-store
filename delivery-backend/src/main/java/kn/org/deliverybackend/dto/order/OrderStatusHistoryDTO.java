package kn.org.deliverybackend.dto.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusHistoryDTO {
    private UUID id;
    private UUID orderId;
    private String fromStatus;
    private String toStatus;
    private String note;
    private String changedBy;
    private LocalDateTime createdAt;
}
