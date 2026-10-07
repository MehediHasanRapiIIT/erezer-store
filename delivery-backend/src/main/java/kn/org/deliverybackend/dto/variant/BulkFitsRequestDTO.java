package kn.org.deliverybackend.dto.variant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kn.org.deliverybackend.enumeration.StockScope;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** The same fits for many products at once: the ones chosen, or a whole category. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkFitsRequestDTO {

    /** PRODUCTS or CATEGORY. */
    @NotNull(message = "Choose what to change")
    private StockScope scope;

    @Size(max = 500, message = "Up to 500 products at a time")
    private List<Long> productIds;

    private Long categoryId;

    /** DROP_SHOULDER and/or REGULAR_FIT; empty for neither. */
    @NotNull(message = "Say which fits the products come in")
    @Size(max = 2, message = "A product comes in at most two fits")
    private List<String> fits;
}
