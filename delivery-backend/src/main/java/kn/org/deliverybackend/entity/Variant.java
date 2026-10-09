package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "variant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Variant extends AbstractBaseEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "category_id")
    private Long categoryId;

    /** @deprecated legacy column from the delivery-app era. New code reads {@link #stockQuantity}. */
    @Deprecated
    private Long quantity;

    private String name;

    @Column(name = "shop_id")
    private Long shopId;

    // ── Phase 3 clothing-specific fields ───────────────────────────────────────
    /** e.g. "XS", "S", "M", "L", "XL", "XXL", "28", "30", "32". */
    @Column(length = 16)
    private String size;

    /**
     * The fit this size belongs to: DROP_SHOULDER or REGULAR_FIT. Null for a
     * product that has no fits. A product's sizes all have a fit or none do.
     */
    @Column(name = "fit", length = 30)
    private String fit;

    /**
     * The combination of the product's own options this variant is ("Colour:
     * Black, Sleeve: Long"), as a key of ids - see ProductOptions. Null on a
     * product that has no options. A product's variants all have a combination
     * or none do.
     */
    @Column(name = "option_key", length = 500)
    private String optionKey;

    /** Per-variant SKU; unique-per-product (enforced by DB partial unique index). */
    @Column(length = 64)
    private String sku;

    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    /** Optional override of the product's base price for this variant. */
    @Column(name = "price_override", precision = 12, scale = 2)
    private BigDecimal priceOverride;
}
