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
    /** FIXED_PRICE, BUY_X_GET_Y or QUANTITY_DISCOUNT. */
    private String offerType;
    /** "Any 3 for ৳999", "Buy 2 Get 1 Free", "Buy 2, save 10% · Buy 3, save 15%": the offer in words. */
    private String headline;
    /** The steps of a quantity discount, smallest quantity first; empty for the other kinds. */
    private List<BundleTierDTO> tiers;
    /** The fewest and the most items the customer picks. The same number, except for a quantity discount. */
    private Integer minItems;
    private Integer maxItems;
    private Integer buyCount;
    private Integer getCount;
    /** buyCount + getCount — number of slots the customer fills. For a quantity discount, the fewest. */
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
