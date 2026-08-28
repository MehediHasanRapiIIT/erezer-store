package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "return_request",
        indexes = {
                @Index(name = "idx_return_order",  columnList = "order_id"),
                @Index(name = "idx_return_user",   columnList = "user_id"),
                @Index(name = "idx_return_status", columnList = "status")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnRequest extends AbstractBaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    /** Null for guest-order returns. */
    @Column(name = "user_id")
    private UUID userId;

    /** Snapshot for guest returns so we can reach the customer without a Users row. */
    @Column(name = "customer_email", length = 255)
    private String customerEmail;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, length = 64)
    private String reason;

    @Column(name = "customer_notes", length = 2000)
    private String customerNotes;

    @Column(name = "admin_notes", length = 2000)
    private String adminNotes;

    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decided_by", length = 200)
    private String decidedBy;

    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;
}
