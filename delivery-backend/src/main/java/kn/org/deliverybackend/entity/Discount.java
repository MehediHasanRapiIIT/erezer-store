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

/**
 * An automatic price reduction applied at checkout (distinct from {@link Coupon},
 * which is customer-entered). Scoped to a single product, a whole category, or
 * the entire store. Whether several active discounts combine is controlled per
 * discount via {@link #stackable}; when discounts do not stack, the one with the
 * highest {@link #priority} wins. See {@code DiscountEngine}.
 */
@Entity
@Table(name = "discount")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Discount extends AbstractBaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    /** Persisted as the {@code DiscountScope} name. */
    @Column(nullable = false, length = 16)
    private String scope;

    /** Persisted as the {@code DiscountType} name. */
    @Column(name = "discount_type", nullable = false, length = 16)
    private String discountType;

    @Column(name = "discount_value", precision = 12, scale = 2)
    private BigDecimal discountValue;

    /** productId when scope=PRODUCT, categoryId when scope=CATEGORY, null when GLOBAL. */
    @Column(name = "target_id")
    private Long targetId;

    /** Whether this discount may combine with other stackable discounts. */
    @Column(nullable = false)
    private Boolean stackable;

    /** Higher wins when discounts are not stacked. */
    @Column(nullable = false)
    private Integer priority;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(length = 255)
    private String description;
}
