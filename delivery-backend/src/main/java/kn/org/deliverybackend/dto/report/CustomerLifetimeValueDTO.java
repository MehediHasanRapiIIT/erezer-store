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
    /** Null for a guest shopper, who ordered without an account. */
    private UUID userId;
    /** True when the customer has no account and is known only by their order email. */
    private boolean guest;
    private String customerName;
    private String email;
    private String phone;
    private long orderCount;
    private BigDecimal lifetimeRevenue;
    private BigDecimal averageOrderValue;
    private LocalDateTime firstOrderAt;
    private LocalDateTime lastOrderAt;
    /** When the account was made; null for a guest. */
    private LocalDateTime joinedAt;
}
