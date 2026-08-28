package kn.org.deliverybackend.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Published right after an order is created and committed. Listeners (email,
 * webhook, analytics) react asynchronously — never inline with the request.
 */
@Getter
public class OrderPlacedEvent extends ApplicationEvent {

    private final UUID orderId;
    private final String customerEmail;

    public OrderPlacedEvent(Object source, UUID orderId, String customerEmail) {
        super(source);
        this.orderId = orderId;
        this.customerEmail = customerEmail;
    }
}
