package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Order extends AbstractBaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID clientId;

    private UUID riderId;

    private String deliveryAddress;

    private BigDecimal totalAmount;

    private String paymentMethod;

    private UUID paymentId;

    /**
     * Persisted as a String column (varchar) for backwards compatibility with
     * legacy "PENDING" rows. Validate / parse via
     * {@link kn.org.deliverybackend.enumeration.OrderStatus#parse(String)} at
     * service boundaries.
     */
    private String orderStatus;

    private Long shopId;

    private Double deliveryCharge;

    private Float latitude;

    private Float longitude;

    // ── Phase 2 additions (additive only) ──────────────────────────────────────
    @Column(name = "shipping_fee")
    private BigDecimal shippingFee;

    @Column(name = "tax_amount")
    private BigDecimal taxAmount;

    @Column(name = "courier_name")
    private String courierName;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /**
     * Set automatically by {@code OrderHistoryServiceImpl.updateOrderStatus}
     * when the order transitions to DELIVERED. Used to enforce the return
     * window.
     */
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /**
     * Snapshot of the customer's email at the moment of ordering. Allows the
     * status-change email to reach the right inbox even if the user's profile
     * email later changes — and supports guest checkout, where the order is
     * not linked to a registered Users row.
     */
    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_phone", length = 40)
    private String customerPhone;

    // ── Phase 4: coupon + zone snapshot ────────────────────────────────────────
    @Column(name = "coupon_id")
    private UUID couponId;

    @Column(name = "coupon_code", length = 64)
    private String couponCode;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "subtotal_amount", precision = 12, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(name = "shipping_zone_id")
    private Long shippingZoneId;
}
