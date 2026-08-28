package kn.org.deliverybackend.dto.variant;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VariantRequestDTO {

    @Size(max = 16)
    private String size;

    @Size(max = 64)
    private String sku;

    @PositiveOrZero
    private Integer stockQuantity;

    @PositiveOrZero
    private BigDecimal priceOverride;

    /** Optional display name override (rare; usually derived from size/color). */
    @Size(max = 120)
    private String name;
}
