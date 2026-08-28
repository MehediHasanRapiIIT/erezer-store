package kn.org.deliverybackend.dto.discount;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountResponseDTO {
    private UUID id;
    private String name;
    private String scope;
    private String discountType;
    private BigDecimal discountValue;
    private Long targetId;
    private Boolean stackable;
    private Integer priority;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private Boolean isActive;
    private String description;
}
