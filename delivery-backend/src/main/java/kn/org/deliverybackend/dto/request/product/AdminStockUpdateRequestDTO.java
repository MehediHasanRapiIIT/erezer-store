package kn.org.deliverybackend.dto.request.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import kn.org.deliverybackend.enumeration.StockOperation;
import lombok.Data;

@Data
public class AdminStockUpdateRequestDTO {

    @NotNull(message = "Operation must not be null")
    private StockOperation operation;

    @NotNull(message = "Quantity must not be null")
    @Min(value = 0, message = "Quantity must be >= 0")
    private Integer quantity;

    /** Optional — if provided, updates the unit label on the inventory row */
    private String unit;

    /** Optional — if provided, updates the low stock threshold */
    @Min(value = 0, message = "Low stock threshold must be >= 0")
    private Integer lowStockThreshold;
}
