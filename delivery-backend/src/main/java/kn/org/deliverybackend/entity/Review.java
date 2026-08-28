package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "review",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_review_user_product_order",
                columnNames = {"user_id", "product_id", "order_id"}
        ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Review extends AbstractBaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "TEXT")
    private String comment;
}
