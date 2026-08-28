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
public class PaymentDTO {

    private UUID id;

    private UUID orderId;

    private String method;

    private String transactionId;

    private BigDecimal amount;

    private String status;

    private LocalDateTime createdAt;
}
