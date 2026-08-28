package kn.org.deliverybackend.event;

import kn.org.deliverybackend.enumeration.ReturnStatus;
import kn.org.deliverybackend.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReturnEmailListener {

    private final EmailService emailService;

    @Value("${app.frontend.store-url}")
    private String storeUrl;

    @EventListener
    public void onReturnDecision(ReturnDecisionEvent event) {
        if (event.getCustomerEmail() == null) {
            return;
        }
        Map<String, Object> vars = new HashMap<>();
        vars.put("orderId", event.getOrderId());
        vars.put("returnId", event.getReturnRequestId());
        vars.put("statusLabel", humanLabel(event.getStatus()));
        vars.put("headline", headlineFor(event.getStatus()));
        vars.put("statusMessage", messageFor(event.getStatus()));
        vars.put("adminNotes", event.getAdminNotes());
        vars.put("orderUrl", storeUrl + "/orders/" + event.getOrderId());
        vars.put("refundAmount", event.getRefundAmount() != null
                ? NumberFormat.getCurrencyInstance(Locale.US).format(event.getRefundAmount())
                : null);

        String subject = "Update on your Erezer return";
        emailService.send(event.getCustomerEmail(), subject, "return-decision", vars);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private String humanLabel(ReturnStatus s) {
        return switch (s) {
            case REQUESTED -> "Requested";
            case APPROVED  -> "Approved";
            case REJECTED  -> "Rejected";
            case PICKED_UP -> "Picked up";
            case REFUNDED  -> "Refunded";
        };
    }

    private String headlineFor(ReturnStatus s) {
        return switch (s) {
            case REQUESTED -> "We've received your return request";
            case APPROVED  -> "Your return is approved";
            case REJECTED  -> "We couldn't approve this return";
            case PICKED_UP -> "We've collected your return";
            case REFUNDED  -> "Your refund has been processed";
        };
    }

    private String messageFor(ReturnStatus s) {
        return switch (s) {
            case REQUESTED -> "Thanks — our team will review the photos and notes you sent and get back to you shortly.";
            case APPROVED  -> "Please pack the items as-is. We'll arrange courier pickup in the next 1-2 business days.";
            case REJECTED  -> "Unfortunately we couldn't proceed with this return. The notes from our team are below.";
            case PICKED_UP -> "The items are with us. We'll inspect and process the refund within 2-3 business days.";
            case REFUNDED  -> "The refund is on its way — please allow a few business days for it to reach your account.";
        };
    }
}
