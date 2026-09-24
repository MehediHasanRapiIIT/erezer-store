package kn.org.deliverybackend.dto.request.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kn.org.deliverybackend.enumeration.StockOperation;
import kn.org.deliverybackend.enumeration.StockScope;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** One stock change applied to chosen products, a whole category, or every product. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkStockAdjustRequestDTO {

    @NotNull(message = "Choose what to update")
    private StockScope scope;

    /** Required for {@code PRODUCTS}; ignored otherwise. */
    @Size(max = 500, message = "Up to 500 products at a time")
    private List<Long> productIds;

    /** Required for {@code CATEGORY}; ignored otherwise. */
    private Long categoryId;

    @NotNull(message = "Choose add, remove or set")
    private StockOperation operation;

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity must be 0 or more")
    @Max(value = 1_000_000, message = "That quantity is too large")
    private Integer quantity;
}
