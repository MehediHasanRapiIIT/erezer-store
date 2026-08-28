package kn.org.deliverybackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kn.org.deliverybackend.dto.response.product.ProductResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for home page data")
public class HomePageResponseDTO {
    @Schema(description = "List of promotional banners")
    private List<PromotionalBannerDTO> banners;

    @Schema(description = "List of categories")
    private List<CategoryDTO> categories;

    @Schema(description = "List of popular items")
    private List<ProductResponseDTO> popularItems;

    @Schema(description = "List of featured items")
    private List<ProductResponseDTO> featuredItems;

    @Schema(description = "List of admin-flagged new-arrival items")
    private List<ProductResponseDTO> newArrivalItems;
}
