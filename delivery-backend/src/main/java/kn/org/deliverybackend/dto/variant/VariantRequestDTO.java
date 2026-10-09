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

    /**
     * DROP_SHOULDER or REGULAR_FIT, for a product that comes in fits; null
     * otherwise. When changing a size, null leaves its fit as it is.
     */
    @Size(max = 30)
    private String fit;

    @Size(max = 64)
    private String sku;

    @PositiveOrZero
    private Integer stockQuantity;

    @PositiveOrZero
    private BigDecimal priceOverride;

    /**
     * The combination of the product's own options this variant is: option id
     * to the id of the chosen value. Needed for a new variant of a product that
     * has options; ignored when changing one, which keeps its combination.
     */
    private java.util.Map<String, String> options;

    /** Optional display name override (rare; usually derived from size/color). */
    @Size(max = 120)
    private String name;
}
