package kn.org.deliverybackend.event;

import kn.org.deliverybackend.enumeration.StockStatus;
import org.springframework.context.ApplicationEvent;

public class StockUpdateEvent extends ApplicationEvent {

    private final Long productId;
    private final int stockQuantity;
    private final StockStatus stockStatus;

    public StockUpdateEvent(Object source, Long productId, int stockQuantity, StockStatus stockStatus) {
        super(source);
        this.productId = productId;
        this.stockQuantity = stockQuantity;
        this.stockStatus = stockStatus;
    }

    public Long getProductId() { return productId; }
    public int getStockQuantity() { return stockQuantity; }
    public StockStatus getStockStatus() { return stockStatus; }
}
