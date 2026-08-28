package kn.org.deliverybackend.event;

import kn.org.deliverybackend.service.EmailAttachment;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;
import java.util.UUID;

/**
 * Published after a custom-design quote request is persisted. The
 * {@link CustomOrderEmailListener} notifies the configured admin address,
 * attaching the flattened per-view preview PNGs carried here.
 */
@Getter
public class CustomOrderCreatedEvent extends ApplicationEvent {

    private final UUID customOrderId;
    private final String reference;
    private final String customerName;
    private final String email;
    private final String phone;
    private final String shippingBlock;
    private final String itemName;
    private final String colorName;
    private final String size;
    private final String printTechnique;
    private final String notesHtml;
    private final List<EmailAttachment> attachments;

    public CustomOrderCreatedEvent(Object source,
                                   UUID customOrderId,
                                   String reference,
                                   String customerName,
                                   String email,
                                   String phone,
                                   String shippingBlock,
                                   String itemName,
                                   String colorName,
                                   String size,
                                   String printTechnique,
                                   String notesHtml,
                                   List<EmailAttachment> attachments) {
        super(source);
        this.customOrderId = customOrderId;
        this.reference = reference;
        this.customerName = customerName;
        this.email = email;
        this.phone = phone;
        this.shippingBlock = shippingBlock;
        this.itemName = itemName;
        this.colorName = colorName;
        this.size = size;
        this.printTechnique = printTechnique;
        this.notesHtml = notesHtml;
        this.attachments = attachments;
    }
}
