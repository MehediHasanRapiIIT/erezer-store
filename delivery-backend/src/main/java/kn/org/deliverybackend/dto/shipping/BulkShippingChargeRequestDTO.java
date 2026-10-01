package kn.org.deliverybackend.dto.shipping;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kn.org.deliverybackend.enumeration.StockScope;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * One delivery-charge change, made from the admin Products page.
 *
 * <p>Where the charge lands depends on the scope: chosen products and the whole
 * shop set it on the products themselves, while a category sets it on the
 * category, so products added to it later are covered too.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkShippingChargeRequestDTO {

    @NotNull(message = "Choose what to change")
    private StockScope scope;

    /** Required for {@code PRODUCTS}; ignored otherwise. */
    @Size(max = 500, message = "Up to 500 products at a time")
    private List<Long> productIds;

    /** Required for {@code CATEGORY}; ignored otherwise. */
    private Long categoryId;

    /**
     * What delivery costs, in taka. Zero means delivered free. Ignored — and not
     * required — when {@link #useAreaPrice} is true.
     */
    @DecimalMin(value = "0", message = "A delivery charge can't be negative.")
    @DecimalMax(value = "100000", message = "That delivery charge is too large.")
    private BigDecimal charge;

    /**
     * True to take the charge away again, so the customer's area price (Inside
     * Dhaka / Outside Dhaka) decides. This is why {@link #charge} can't simply be
     * null for "none": null and zero mean very different things here.
     */
    private boolean useAreaPrice;
}
