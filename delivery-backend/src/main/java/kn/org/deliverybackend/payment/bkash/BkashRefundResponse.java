package kn.org.deliverybackend.payment.bkash;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** bKash's answer to a refund: "Completed" with its refund transaction id, or bKash's reason for refusing. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BkashRefundResponse {
    private String status;
    private String refundTrxId;
    private BigDecimal amount;
    private String errorMessage;

    public boolean completed() {
        return "Completed".equalsIgnoreCase(status);
    }
}
