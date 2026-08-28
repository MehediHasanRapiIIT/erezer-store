package kn.org.deliverybackend.dto.bundle;

import kn.org.deliverybackend.dto.response.product.ProductResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BundleOfferResponseDTO {
    private UUID id;
    private String name;
    private String label;
    private String description;
    private Integer buyCount;
    private Integer getCount;
    /** buyCount + getCount — number of slots the customer fills. */
    private Integer slots;
    private BigDecimal bundlePrice;
    private BigDecimal compareAtPrice;
    /** compareAtPrice − bundlePrice (null when no compare price). */
    private BigDecimal savings;
    private Boolean isActive;
    private Boolean featured;
    private Integer sortOrder;
    private List<String> images;
    /** Curated products the customer can choose from (enriched for the picker). */
    private List<ProductResponseDTO> products;
}
