package kn.org.deliverybackend.dto.request.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BulkStockUpdateRequestDTO {

    @NotEmpty(message = "Updates list must not be empty")
    @Valid
    private List<BulkStockItem> updates;

    @Data
    public static class BulkStockItem {
        private Long productId;
        private int quantity;   // always SET operation for bulk
        private String unit;
        private Integer lowStockThreshold;
    }
}

