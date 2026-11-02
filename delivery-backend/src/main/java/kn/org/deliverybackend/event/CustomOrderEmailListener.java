package kn.org.deliverybackend.event;

import kn.org.deliverybackend.entity.StoreSettings;
import kn.org.deliverybackend.repository.StoreSettingsRepository;
import kn.org.deliverybackend.service.EmailService;
import kn.org.deliverybackend.service.impl.EmailServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Emails the shop whenever a custom-design quote request is submitted, attaching the
 * pictures of the design. It goes to the address set on the server
 * (CUSTOM_DESIGN_ADMIN_EMAIL), or, when that is left as the placeholder, to the support
 * email in admin Settings. The actual send is async (see {@link EmailService}), so this
 * listener returns quickly.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CustomOrderEmailListener {

    private final EmailService emailService;
    private final StoreSettingsRepository storeSettings;

    @Value("${app.custom-design.admin-email}")
    private String adminEmail;

    @EventListener
    public void onCustomOrderCreated(CustomOrderCreatedEvent event) {
        String to = recipient();
        if (to == null) {
            log.warn("No address for custom design requests (server setting or Settings support email); skipping {}", event.getReference());
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
        emailService.sendWithAttachments(to, subject, "custom-design-request", vars, event.getAttachments());
    }

    /**
     * Where the request goes. The server's default, custom@erezer.local, is a placeholder
     * that no mailbox answers: mail sent there only bounces back.
     */
    String recipient() {
        String configured = EmailServiceImpl.validAddress(adminEmail);
        if (configured != null && !configured.toLowerCase().endsWith(".local")) return configured;
        StoreSettings shop = storeSettings.findById(StoreSettings.SINGLETON_ID).orElse(null);
        return shop == null ? null : EmailServiceImpl.validAddress(shop.getSupportEmail());
    }
}
