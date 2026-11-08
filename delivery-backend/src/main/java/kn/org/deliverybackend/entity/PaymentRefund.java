package kn.org.deliverybackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Money sent back to a customer's bKash wallet through bKash's Refund API. Failed
 * tries are kept with bKash's message; only COMPLETED ones count as refunded.
 */
@Entity
@Table(name = "payment_refund")
@Getter
@Setter
@NoArgsConstructor
public class PaymentRefund {

    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    /** Set when the refund was made by marking a return refunded. */
    @Column(name = "return_request_id")
    private UUID returnRequestId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false, length = 32)
    private String status;

    /** bKash's id for the refund itself (not the original payment's). */
    @Column(name = "refund_trx_id", length = 128)
    private String refundTrxId;

    /** bKash's words when it refused. */
    @Column(length = 500)
    private String message;

    /** The staff member who asked for it. */
    @Column(name = "requested_by", length = 200)
    private String requestedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
