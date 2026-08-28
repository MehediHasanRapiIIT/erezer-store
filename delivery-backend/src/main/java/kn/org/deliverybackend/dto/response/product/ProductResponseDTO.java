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
}
