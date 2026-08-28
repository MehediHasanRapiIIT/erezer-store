package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Audit row for every order-status change. Inserted on order creation
 * (with {@code fromStatus == null}) and on every subsequent transition.
 */
@Entity
@Table(name = "order_status_history",
        indexes = {
                @Index(name = "idx_osh_order", columnList = "order_id"),
                @Index(name = "idx_osh_created", columnList = "created_at"),
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusHistory extends AbstractBaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    /** Null on the initial "order placed" row. */
    @Column(name = "from_status", length = 32)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 32)
    private String toStatus;

    /** Optional human-readable note from the admin. */
    @Column(name = "note", length = 1000)
    private String note;

    /**
     * Free-text identity of whoever triggered the change (admin email, user id,
     * "customer", "system"). Kept as a String so we don't have to FK-link to
     * the various identity tables.
     */
    @Column(name = "changed_by", length = 200)
    private String changedBy;
}
