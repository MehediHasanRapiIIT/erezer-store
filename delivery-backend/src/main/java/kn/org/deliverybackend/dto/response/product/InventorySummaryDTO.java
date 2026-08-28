package kn.org.deliverybackend.dto.response.product;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventorySummaryDTO {
    /** Products with stockQuantity > 0 but <= lowStockThreshold */
    private int criticalLow;
    /** Products with stockQuantity == 0 */
    private int outOfStock;
    /** Products where stock <= threshold (LOW_STOCK or OUT_OF_STOCK) — needs reorder */
    private int reorderPending;
}
