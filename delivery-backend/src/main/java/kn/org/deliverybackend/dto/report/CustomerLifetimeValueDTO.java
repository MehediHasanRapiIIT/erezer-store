package kn.org.deliverybackend.dto.report;

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
public class CustomerLifetimeValueDTO {
    private UUID userId;
    private String customerName;
    private String email;
    private long orderCount;
    private BigDecimal lifetimeRevenue;
    private BigDecimal averageOrderValue;
    private LocalDateTime firstOrderAt;
    private LocalDateTime lastOrderAt;
}
