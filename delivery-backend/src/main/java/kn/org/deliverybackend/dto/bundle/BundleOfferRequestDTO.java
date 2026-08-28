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

    @NotNull
    @Min(1)
    private Integer buyCount;

    @NotNull
    @Min(0)
    private Integer getCount;

    @NotNull
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
