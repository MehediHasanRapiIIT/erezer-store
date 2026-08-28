package kn.org.deliverybackend.event;

import kn.org.deliverybackend.enumeration.ReturnStatus;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class ReturnDecisionEvent extends ApplicationEvent {

    private final UUID returnRequestId;
    private final UUID orderId;
    private final String customerEmail;
    private final ReturnStatus status;
    private final String adminNotes;
    private final BigDecimal refundAmount;

    public ReturnDecisionEvent(Object source,
                                UUID returnRequestId,
                                UUID orderId,
                                String customerEmail,
                                ReturnStatus status,
                                String adminNotes,
                                BigDecimal refundAmount) {
        super(source);
        this.returnRequestId = returnRequestId;
        this.orderId = orderId;
        this.customerEmail = customerEmail;
        this.status = status;
        this.adminNotes = adminNotes;
        this.refundAmount = refundAmount;
    }
}
