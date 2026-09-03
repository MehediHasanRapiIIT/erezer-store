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
}
