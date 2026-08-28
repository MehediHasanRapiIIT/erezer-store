package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "return_item",
        indexes = {
                @Index(name = "idx_return_item_request", columnList = "return_request_id")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnItem extends AbstractBaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "return_request_id", nullable = false)
    private UUID returnRequestId;

    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    /** SEALED | OPENED | DAMAGED | OTHER */
    @Column(length = 32)
    private String condition;

    @Column(name = "line_refund_amount", precision = 12, scale = 2)
    private BigDecimal lineRefundAmount;
}
