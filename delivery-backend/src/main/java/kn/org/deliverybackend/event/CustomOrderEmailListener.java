package kn.org.deliverybackend.event;

import kn.org.deliverybackend.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Emails the configured admin address whenever a custom-design quote request is
 * submitted, attaching the flattened design previews. The actual send is async
 * (see {@link EmailService}), so this listener returns quickly.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CustomOrderEmailListener {

    private final EmailService emailService;

    @Value("${app.custom-design.admin-email}")
    private String adminEmail;

    @EventListener
    public void onCustomOrderCreated(CustomOrderCreatedEvent event) {
        if (adminEmail == null || adminEmail.isBlank()) {
            log.warn("custom-design admin email not configured; skipping notification for {}", event.getReference());
            return;
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("reference", event.getReference());
        vars.put("customerName", event.getCustomerName());
        vars.put("email", event.getEmail());
        vars.put("phone", event.getPhone());
        vars.put("shippingBlock", event.getShippingBlock());
        vars.put("itemName", event.getItemName());
        vars.put("colorName", event.getColorName());
        vars.put("size", event.getSize());
        vars.put("printTechnique", event.getPrintTechnique());
        vars.put("notes", event.getNotesHtml());

        String subject = "New custom design request " + event.getReference();
        emailService.sendWithAttachments(adminEmail, subject, "custom-design-request", vars, event.getAttachments());
    }
}
