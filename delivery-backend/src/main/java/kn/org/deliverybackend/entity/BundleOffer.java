package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An admin-managed "Buy X Get Y" bundle deal (e.g. Buy 2 Get 1 Free). The
 * customer fills {@code buyCount + getCount} slots from the curated
 * {@link #productIds} and pays the fixed {@link #bundlePrice}; the difference
 * between the selected items' regular prices and {@code bundlePrice} is applied
 * as a discount on the order total (server-enforced).
 */
@Entity
@Table(name = "bundle_offer",
        indexes = {
                @Index(name = "idx_bundle_active", columnList = "is_active, sort_order"),
                @Index(name = "idx_bundle_featured", columnList = "featured")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BundleOffer extends AbstractBaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    /** Small eyebrow label, e.g. "Limited time". */
    @Column(length = 120)
    private String label;

    @Column(columnDefinition = "text")
    private String description;

    /** How many items the customer pays for. */
    @Column(name = "buy_count", nullable = false)
    private Integer buyCount;

    /** How many items are free on top of the paid ones. */
    @Column(name = "get_count", nullable = false)
    private Integer getCount;

    /** Fixed total the customer is charged for the whole bundle. */
    @Column(name = "bundle_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal bundlePrice;

    /** Strikethrough "was" price shown on the offer. */
    @Column(name = "compare_at_price", precision = 12, scale = 2)
    private BigDecimal compareAtPrice;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = Boolean.TRUE;

    @Column(nullable = false)
    @Builder.Default
    private Boolean featured = Boolean.FALSE;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    /** Gallery images for the bundle's own page. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "bundle_offer_image",
            joinColumns = @JoinColumn(name = "bundle_offer_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "image_url", length = 1000)
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    /** Curated products the customer may pick from, in the admin's order. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "bundle_offer_product",
            joinColumns = @JoinColumn(name = "bundle_offer_id"))
    @OrderColumn(name = "position")
    @Column(name = "product_id", nullable = false)
    @Builder.Default
    private List<Long> productIds = new ArrayList<>();

    /** Total slots the customer must fill. */
    @Transient
    public int slots() {
        return (buyCount == null ? 0 : buyCount) + (getCount == null ? 0 : getCount);
    }
}
