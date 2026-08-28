package kn.org.deliverybackend.dto.customdesign;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Compact custom-order row for the admin list. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomOrderSummaryDTO {
    private UUID id;
    private String reference;
    private String customerName;
    private String email;
    private String phone;
    private String itemName;
    private String status;
    private String thumbnailUrl;
    private LocalDateTime createdAt;
}
