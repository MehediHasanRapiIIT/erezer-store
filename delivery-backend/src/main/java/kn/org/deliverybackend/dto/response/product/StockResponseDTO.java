package kn.org.deliverybackend.dto.response.product;

import kn.org.deliverybackend.enumeration.StockStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockResponseDTO {
    private Long productId;
    private String productName;
    private String sku;
    private String imageUrl;
    private String unit;
    private int stockQuantity;
    private StockStatus stockStatus;
    private Integer lowStockThreshold;
}
