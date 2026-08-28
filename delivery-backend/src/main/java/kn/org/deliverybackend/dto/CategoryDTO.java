package kn.org.deliverybackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO for category information")
public class CategoryDTO {
    @Schema(description = "Category ID", example = "1")
    private Long id;

    @Schema(description = "Category name", example = "Electronics")
    private String name;

    @Schema(description = "Indicates if category is active", example = "true")
    private Boolean isActive;

    @Schema(description = "Category image URL")
    private String imageUrl;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;
}
