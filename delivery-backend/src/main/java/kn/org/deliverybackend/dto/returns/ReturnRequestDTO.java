package kn.org.deliverybackend.dto.returns;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnRequestDTO {
    private UUID id;
    private UUID orderId;
    private UUID userId;
    private String customerEmail;
    private String status;
    private String reason;
    private String customerNotes;
    private String adminNotes;
    private BigDecimal refundAmount;
    private LocalDateTime requestedAt;
    private LocalDateTime decidedAt;
    private String decidedBy;
    private LocalDateTime pickedUpAt;
    private LocalDateTime refundedAt;
    private List<ReturnItemDTO> items;
    private List<ReturnPhotoDTO> photos;
}
