package kn.org.deliverybackend.dto.request.product;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequestDTO {

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    @NotBlank(message = "Product name is required")
    @Size(min = 2, max = 100, message = "Product name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than zero")
    private BigDecimal price;

    @DecimalMin(value = "0.0", message = "Discount percentage cannot be negative")
    @DecimalMax(value = "100.0", message = "Discount percentage cannot exceed 100")
    private BigDecimal discountPercentage;

    @NotNull(message = "Shop ID is required")
    private Long shopId;

    private String imageUrl;

    private String unit; // e.g. kg, packets, items, litre

    @NotNull(message = "Availability status is required")
    private Boolean isAvailable;

    @Min(value = 0, message = "Low stock threshold must be >= 0")
    private Integer lowStockThreshold;

    /** Admin flag: show on the home "New arrivals" section. */
    private Boolean isNewArrival;

    /** Admin flag: show on the home "Featured products" section. */
    private Boolean isFeatured;

    // Clothing attributes (Phase 3 catalog). All optional.
    private String brand;
    private String gender;
    private String material;
    private String careInstructions;

    // Custom (made-to-order) sizing. All optional.
    private Boolean customSizeEnabled;
    private BigDecimal customSizeSurcharge;
    private String customSizeNote;
}
