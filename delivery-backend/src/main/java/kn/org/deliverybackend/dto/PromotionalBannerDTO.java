package kn.org.deliverybackend.dto;

import kn.org.deliverybackend.enumeration.BannerSlot;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO for promotional banner information")
public class PromotionalBannerDTO {
    @Schema(description = "Banner ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    
    @Schema(description = "Banner image URL", example = "https://example.com/images/banner.jpg")
    private String imageUrl;
    
    @Schema(description = "Promotion start date", example = "2024-01-01")
    private LocalDate fromDate;
    
    @Schema(description = "Promotion end date", example = "2024-12-31")
    private LocalDate toDate;
    
    @Schema(description = "Promotion title", example = "Winter Sale")
    private String promotionTitle;
    
    @Schema(description = "Promotion details", example = "Up to 50% off on all products")
    private String promotionDetails;

    @Schema(description = "Home-page band this banner fills; null behaves as HERO",
            example = "SPLIT_LEFT")
    private BannerSlot slot;

    @Schema(description = "Button text; null hides the button", example = "SHOP NOW")
    private String ctaLabel;

    @Schema(description = "Button destination; null hides the button", example = "/shop?category=2")
    private String ctaLink;

    @Schema(description = "Ordering within a slot, lowest first", example = "0")
    private Integer sortOrder;
}
