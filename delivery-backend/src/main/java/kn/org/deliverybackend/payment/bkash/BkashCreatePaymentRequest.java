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
public class BkashCreatePaymentRequest {
    /** The order being paid for — used as merchant invoice number. */
    private String orderId;
    private BigDecimal amount;
    private String currency;     // "BDT"
    private String intent;       // "sale"
    private String callbackUrl;
    private String customerEmail;
    /** Shown on bKash's page: the customer's mobile number fills in the wallet box. */
    private String payerReference;
    /** Unique for each attempt: an order paid for a second time needs a new one. */
    private String invoiceNumber;
}
