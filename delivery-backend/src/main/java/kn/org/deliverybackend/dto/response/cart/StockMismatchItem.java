package kn.org.deliverybackend.dto.response.cart;

import kn.org.deliverybackend.enumeration.StockStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockMismatchItem {
    private UUID productId;
    private int requestedQuantity;
    private int availableQuantity;
    private StockStatus stockStatus;
}
