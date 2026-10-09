package kn.org.deliverybackend.dto.response.product;

import kn.org.deliverybackend.enumeration.StockStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class StockResponseDTO {
    private Long productId;
    private String productName;
    private String sku;
    private String imageUrl;
    private String unit;
    private int stockQuantity;
    private StockStatus stockStatus;
    private Integer lowStockThreshold;
    /** Typed by staff; several products may share one. */
    private String productCode;
    /**
     * The product's sizes and the stock of each, in the order customers see
     * them. Empty for a product not sold in sizes; when there are any,
     * {@link #stockQuantity} is their total.
     */
    private List<SizeStock> sizes = List.of();

    public StockResponseDTO(Long productId, String productName, String sku, String imageUrl, String unit,
                            int stockQuantity, StockStatus stockStatus, Integer lowStockThreshold,
                            String productCode) {
        this.productId = productId;
        this.productName = productName;
        this.sku = sku;
        this.imageUrl = imageUrl;
        this.unit = unit;
        this.stockQuantity = stockQuantity;
        this.stockStatus = stockStatus;
        this.lowStockThreshold = lowStockThreshold;
        this.productCode = productCode;
    }

    /** One size of a product and how many of it there are. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SizeStock {
        private Long variantId;
        private String size;
        private int stockQuantity;
        /** DROP_SHOULDER or REGULAR_FIT; null when the product has no fits. */
        private String fit;
        /** "Drop Shoulder", for showing. */
        private String fitLabel;
        /** "Black / Long": the combination of the product's own options; null when it has none. */
        private String optionLabel;

        public SizeStock(Long variantId, String size, int stockQuantity) {
            this(variantId, size, stockQuantity, null, null, null);
        }

        public SizeStock(Long variantId, String size, int stockQuantity, String fit, String fitLabel) {
            this(variantId, size, stockQuantity, fit, fitLabel, null);
        }
    }
}
