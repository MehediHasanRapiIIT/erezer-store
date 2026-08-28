package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A time-boxed promotional campaign surfaced on the storefront landing-page
 * widget and the dedicated <em>/flash-sale</em> page: a headline offer
 * ("20% OFF on all items"), a countdown to {@link #endsAt}, and a curated set of
 * participating product ids. The storefront shows the single active campaign
 * whose window covers "now" (see {@code FlashSaleService#getActivePublic}).
 *
 * <p>Distinct from {@link Discount}: a flash sale is purely presentational —
 * the authoritative checkout price still comes from discounts/coupons. The
 * headline {@link #discountType}/{@link #discountValue} drive the strike-through
 * shown on the sale cards.</p>
 */
@Entity
@Table(name = "flash_sale")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSale extends AbstractBaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    /** Small eyebrow shown above the title, e.g. "Limited time". */
    @Column(length = 120)
    private String label;

    /** Persisted as the {@code DiscountType} name (PERCENT | FLAT). */
    @Column(name = "discount_type", nullable = false, length = 16)
    private String discountType;

    @Column(name = "discount_value", precision = 12, scale = 2, nullable = false)
    private BigDecimal discountValue;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    /** Optional coupon shown in the "copy code" banner. */
    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @Column(name = "min_spend", precision = 12, scale = 2)
    private BigDecimal minSpend;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /** When several sales are active, the one shown in the landing-page widget. */
    @ColumnDefault("false")
    @Column(name = "featured", nullable = false)
    private Boolean featured;

    /** Participating product ids, ordered as the admin arranged them. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "flash_sale_product",
            joinColumns = @JoinColumn(name = "flash_sale_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "product_id", nullable = false)
    @Builder.Default
    private List<Long> productIds = new ArrayList<>();
}
