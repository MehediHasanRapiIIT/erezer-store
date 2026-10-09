package kn.org.deliverybackend.dto.variant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariantResponseDTO {
    private Long id;
    private Long productId;
    private String name;
    private String size;
    /** DROP_SHOULDER or REGULAR_FIT; null when the product has no fits. */
    private String fit;
    /** "Drop Shoulder", for showing. */
    private String fitLabel;
    private String sku;
    private Integer stockQuantity;
    private BigDecimal priceOverride;
    /** The combination of the product's own options, as a key of ids; null when the product has none. */
    private String optionKey;
    /** That combination spelt out, in the order the options are shown: Colour: Black, Sleeve: Long. Empty for none. */
    private java.util.List<VariantOptionDTO> options;
    /** "Black / Long": the combination in words; null for none. */
    private String optionLabel;
}
