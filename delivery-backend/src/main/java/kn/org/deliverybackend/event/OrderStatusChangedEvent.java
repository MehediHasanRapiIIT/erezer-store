package kn.org.deliverybackend.event;

import kn.org.deliverybackend.enumeration.OrderStatus;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class OrderStatusChangedEvent extends ApplicationEvent {

    private final UUID orderId;
    private final String customerEmail;
    private final OrderStatus fromStatus;
    private final OrderStatus toStatus;
    private final String note;
    private final String courierName;
    private final String trackingNumber;

    public OrderStatusChangedEvent(Object source,
                                   UUID orderId,
                                   String customerEmail,
                                   OrderStatus fromStatus,
                                   OrderStatus toStatus,
                                   String note,
                                   String courierName,
                                   String trackingNumber) {
        super(source);
        this.orderId = orderId;
        this.customerEmail = customerEmail;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.note = note;
        this.courierName = courierName;
        this.trackingNumber = trackingNumber;
    }
}
