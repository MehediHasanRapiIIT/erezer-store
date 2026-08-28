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
    private String sku;
    private Integer stockQuantity;
    private BigDecimal priceOverride;
}
