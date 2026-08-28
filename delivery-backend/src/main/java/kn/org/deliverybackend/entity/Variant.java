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

    /** Per-variant SKU; unique-per-product (enforced by DB partial unique index). */
    @Column(length = 64)
    private String sku;

    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    /** Optional override of the product's base price for this variant. */
    @Column(name = "price_override", precision = 12, scale = 2)
    private BigDecimal priceOverride;
}
