package kn.org.deliverybackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiderEarningsDTO {

    private UUID id;

    private UUID riderId;

    private UUID orderId;

    private BigDecimal earningAmount;

    private LocalDateTime createdAt;
}

