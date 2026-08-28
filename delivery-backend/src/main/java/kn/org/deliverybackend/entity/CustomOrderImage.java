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
 * A flattened preview image for one view (front/back/leftSleeve/rightSleeve) of
 * a {@link CustomOrder}. Stored so the admin panel can show what the customer
 * designed; the same bytes are attached to the notification email.
 */
@Entity
@Table(name = "custom_order_image",
        indexes = {
                @Index(name = "idx_custom_order_image_order", columnList = "custom_order_id")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomOrderImage extends AbstractBaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "custom_order_id", nullable = false)
    private CustomOrder customOrder;

    /** front | back | leftSleeve | rightSleeve */
    @Column(name = "view_name", nullable = false, length = 40)
    private String viewName;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;
}
