package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "shipping_zone")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingZone extends AbstractBaseEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "country_code", nullable = false, length = 8)
    private String countryCode;

    /**
     * Comma-separated keywords matched against the customer's delivery_address
     * (case-insensitive). Empty / null = catch-all. The default zone is the
     * fallback when nothing matches.
     */
    @Column(name = "region_keywords", length = 2000)
    private String regionKeywords;

    @Column(name = "flat_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal flatFee;

    /** If subtotal &ge; this, shipping is free. Null = never free. */
    @Column(name = "free_above", precision = 12, scale = 2)
    private BigDecimal freeAbove;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}
