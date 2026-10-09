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

    /** What the product page says about it; may run to several lines. */
    @Column(length = 2000)
    private String description;

    @Column(unique = true)
    private String sku;

    /** Typed by staff; required, and several products may share one. The SKU above stays automatic. */
    @Column(name = "product_code", nullable = false, length = 40)
    private String productCode;

    private String unit;

    private BigDecimal price;

    private BigDecimal discountPrice;

    /**
     * True when the sale discount was typed as an amount in taka rather than a
     * percentage. It decides how the sale comes off a fit's or a size's own
     * price: the same amount, or the same percentage. Null means a percentage.
     */
    @Column(name = "sale_by_amount")
    private Boolean saleByAmount;

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

    /**
     * True to keep this product at full price: the discount engine ignores
     * every automatic discount for it, including store-wide ones. Its own
     * sale price ({@link #discountPrice}) still applies — that is set on the
     * product itself, not a discount rule. Null means not excluded.
     */
    @Column(name = "discount_excluded")
    private Boolean discountExcluded;

    /**
     * What this product costs to deliver, whatever the customer's area. Null
     * means the category's charge decides, and if that is null too, the area's
     * price (Inside Dhaka / Outside Dhaka). Zero means delivered free.
     */
    @Column(name = "shipping_charge", precision = 12, scale = 2)
    private BigDecimal shippingCharge;

    /** Stock on this product's page: follow the category, the quantity, or labels. Null means follow the category. */
    @Enumerated(EnumType.STRING)
    @Column(name = "stock_display", length = 20)
    private kn.org.deliverybackend.enumeration.StockDisplay stockDisplay;

    /**
     * The size chart this product shows, from the shop's library. Null: the
     * chart of its category (or of the nearest category above that names one),
     * else the default chart.
     */
    @Column(name = "size_chart_id")
    private Long sizeChartId;

    /**
     * The options this product comes in beyond size and fit - colour, and
     * anything else the shop defines - with their choices, as JSON (see
     * ProductOptions). Null for a product with none.
     */
    @Column(name = "options_json", columnDefinition = "text")
    private String optionsJson;

    /**
     * For a product that comes in two fits: a different chart for Regular Fit.
     * Null: Regular Fit shows the same chart as the rest of the product.
     */
    @Column(name = "regular_fit_size_chart_id")
    private Long regularFitSizeChartId;

    /** Spaces at either end of the product code are never kept. */
    @PrePersist
    @PreUpdate
    void tidyProductCode() {
        if (productCode != null) productCode = productCode.trim();
    }

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
