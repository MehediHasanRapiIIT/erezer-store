package kn.org.deliverybackend.event;

import kn.org.deliverybackend.enumeration.StockStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockUpdateMessage {
    private Long productId;
    private int stockQuantity;
    private StockStatus stockStatus;
    private Instant timestamp;
}
