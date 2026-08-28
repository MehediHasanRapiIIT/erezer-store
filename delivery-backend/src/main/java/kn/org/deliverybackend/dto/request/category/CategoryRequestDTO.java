package kn.org.deliverybackend.dto.request.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRequestDTO {
    @NotBlank(message = "Category name is required")
    private String name;

    @NotNull(message = "Active status is required")
    private Boolean isActive;

    /** Optional image URL (uploaded via /admin/uploads/image). */
    private String imageUrl;
}
