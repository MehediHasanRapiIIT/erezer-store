package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Product extends AbstractBaseEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id")
    private Long categoryId;

    private String name;

    private String description;

    @Column(unique = true)
    private String sku;

    private String unit;

    private BigDecimal price;

    private BigDecimal discountPrice;

    private Long shopId;

    private String imageUrl;

    private Boolean isAvailable;

    /** Admin flag: surface this product in the home "New arrivals" section. */
    @Column(name = "is_new_arrival")
    private Boolean isNewArrival;

    /** Admin flag: surface this product in the home "Featured products" section. */
    @Column(name = "is_featured")
    private Boolean isFeatured;

    @Column(name = "stock_quantity", nullable = false, columnDefinition = "int default 0 check (stock_quantity >= 0)")
    private int stockQuantity = 0;

    @Column(name = "low_stock_threshold")
    private Integer lowStockThreshold;

    @Column(name = "avg_rating", nullable = false, columnDefinition = "double precision default 0.0")
    private double avgRating = 0.0;

    @Column(name = "total_reviews", nullable = false, columnDefinition = "int default 0")
    private int totalReviews = 0;

    // ── Phase 3 clothing-brand fields ──────────────────────────────────────────
    @Column(length = 120)
    private String brand;

    /** Free-text "MEN" / "WOMEN" / "UNISEX" / "KIDS"; storefront filters on this. */
    @Column(length = 16)
    private String gender;

    @Column(length = 255)
    private String material;

    @Column(name = "care_instructions", length = 2000)
    private String careInstructions;

    // ── Custom (made-to-order) sizing ──────────────────────────────────────────
    /** When true, the storefront offers a "Custom" size with measurement inputs. */
    @Column(name = "custom_size_enabled")
    private Boolean customSizeEnabled;

    /** Flat surcharge added once per custom-size order line (e.g. 70 BDT). */
    @Column(name = "custom_size_surcharge", precision = 12, scale = 2)
    private BigDecimal customSizeSurcharge;

    /** Admin-set note shown on the custom panel, e.g. "Enter Custom Measurements". */
    @Column(name = "custom_size_note", length = 255)
    private String customSizeNote;

    @PostPersist
    public void generateSku() {
        if (this.sku == null) {
            String prefix = (this.name != null && this.name.length() >= 2)
                    ? this.name.substring(0, 2).toUpperCase().replaceAll("[^A-Z]", "X")
                    : "PR";
            this.sku = prefix + "-" + String.format("%05d", this.id);
        }
    }
}
