package kn.org.deliverybackend.dto.bundle;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Admin create/update payload for a bundle offer. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BundleOfferRequestDTO {

    @NotBlank
    @Size(max = 150)
    private String name;

    @Size(max = 120)
    private String label;

    private String description;

    /**
     * FIXED_PRICE, BUY_X_GET_Y or QUANTITY_DISCOUNT. Left out by an older admin
     * panel, which means Buy X Get Y when there are free items and a fixed
     * price otherwise - what its form always meant.
     */
    private String offerType;
    /** The steps of a QUANTITY_DISCOUNT; ignored for the other kinds. */
    private List<BundleTierDTO> tiers = new ArrayList<>();
    // Which of these three a kind needs is checked in the service, with a message in plain words.
    @Min(1)
    private Integer buyCount;
    @Min(0)
    private Integer getCount;
    @DecimalMin("0.0")
    private BigDecimal bundlePrice;

    @DecimalMin("0.0")
    private BigDecimal compareAtPrice;

    private Boolean isActive;
    private Boolean featured;
    private Integer sortOrder;

    private List<String> imageUrls = new ArrayList<>();

    /** Curated product ids the customer can pick from. */
    private List<Long> productIds = new ArrayList<>();
}
