package kn.org.deliverybackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO for product information")
public class ProductDTO {
    @Schema(description = "Product ID", example = "1")
    private Long id;

    @Schema(description = "Category ID", example = "1")
    private Long categoryId;

    @Schema(description = "Product name", example = "Smartphone")
    private String name;

    @Schema(description = "Product description", example = "Latest smartphone with advanced features")
    private String description;

    @Schema(description = "Original price", example = "50000.00")
    private BigDecimal price;

    @Schema(description = "Discounted price", example = "45000.00")
    private BigDecimal discountPrice;

    @Schema(description = "Shop ID", example = "1")
    private Long shopId;

    @Schema(description = "Product image URL", example = "https://example.com/images/product.jpg")
    private String imageUrl;

    @Schema(description = "Indicates if product is available", example = "true")
    private Boolean isAvailable;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;
}
