package kn.org.deliverybackend.payment.bkash;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BkashPaymentResponse {
    /** "Initiated" | "Completed" | "Failed" | "Cancelled" */
    private String status;
    private String paymentId;
    /** URL to redirect the customer to — the bKash hosted payment page. */
    private String bkashURL;
    private String trxId;
    private BigDecimal amount;
    private String currency;
    private String merchantInvoiceNumber;
    /** Free-text error message when status != Initiated/Completed. */
    private String errorMessage;
}
