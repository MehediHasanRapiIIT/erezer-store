package kn.org.deliverybackend.dto.response.product;

import kn.org.deliverybackend.enumeration.StockStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponseDTO {
    private Long id;
    private Long categoryId;
    private String categoryName;
    private String sku;
    /** Typed by staff; several products may share one. */
    private String productCode;
    private String unit;
    private String name;
    private String description;
    private BigDecimal price;
    private BigDecimal discountPrice;
    private String imageUrl;
    private Boolean isAvailable;
    private Boolean isNewArrival;
    private Boolean isFeatured;
    private int stockQuantity;
    private StockStatus stockStatus;
    private double avgRating;
    private int totalReviews;
    private Integer lowStockThreshold;

    // Phase 3 — clothing brand fields
    private String brand;
    private String gender;
    private String material;
    private String careInstructions;

    // Custom (made-to-order) sizing
    private Boolean customSizeEnabled;
    private BigDecimal customSizeSurcharge;
    private String customSizeNote;

    /**
     * True when the admin excluded this product itself from automatic discounts.
     * Round-trips with the edit form, so it is the product's own flag and never
     * inherits its category's.
     */
    private Boolean discountExcluded;

    /**
     * True when the product's category is excluded, which keeps this product at
     * full price too. Read-only; edited on the category, not here. The
     * storefront treats {@code discountExcluded || categoryDiscountExcluded} as
     * "never automatically discounted".
     */
    private Boolean categoryDiscountExcluded;

    /**
     * What this product costs to deliver, set by an admin on the Products page.
     * Null means it has none of its own, so {@link #categoryShippingCharge} or
     * the customer's area price decides. Zero means delivered free.
     */
    private BigDecimal shippingCharge;

    /**
     * What this product's category charges to deliver. Read-only here — it is
     * set on the category — and shown so a product row can say where an
     * inherited charge came from. Null means the category sets none either.
     */
    private BigDecimal categoryShippingCharge;

    /** The product's own choice, for the edit form: CATEGORY, QUANTITY or LABEL. */
    private kn.org.deliverybackend.enumeration.StockDisplay stockDisplay;
    /** Whether the product page shows the quantity, with the category already taken into account. */
    private Boolean showStockQuantity;

    /**
     * The admin Products list only: the stock of each size, so a total such as
     * 3000 can be seen to be five sizes of 600. Left out everywhere else.
     */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.util.List<StockResponseDTO.SizeStock> sizeStock;
}
