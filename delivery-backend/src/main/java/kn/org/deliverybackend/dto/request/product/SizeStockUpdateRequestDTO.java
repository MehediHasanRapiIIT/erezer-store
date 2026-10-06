package kn.org.deliverybackend.dto.request.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** The Inventory page, for a product sold in sizes: an exact stock figure for each size listed. */
@Data
public class SizeStockUpdateRequestDTO {

    @NotEmpty(message = "Give the stock of at least one size")
    @Valid
    private List<Item> sizes;

    /** Optional — if provided, updates the unit label */
    private String unit;

    /** Optional — if provided, updates the low stock threshold */
    @Min(value = 0, message = "Low stock threshold must be >= 0")
    private Integer lowStockThreshold;

    @Data
    public static class Item {
        @NotNull(message = "Which size?")
        private Long variantId;

        @NotNull(message = "Quantity is required")
        @Min(value = 0, message = "Quantity must be 0 or more")
        @Max(value = 1_000_000, message = "That quantity is too large")
        private Integer quantity;
    }
}
