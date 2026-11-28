package kn.org.deliverybackend.integration.invoiceapp;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Everything the admin panel and Erezer Invoice exchange about sending orders. */
public final class InvoiceAppDtos {

    private InvoiceAppDtos() {
    }

    // ── Settings ────────────────────────────────────────────────────────────

    /** Settings → Erezer Invoice. The key itself never comes back, only its last letters. */
    public record Settings(String url, boolean keySaved, String keyHint, boolean connected) {
    }

    /**
     * A change from the Settings page.
     *
     * @param url       Erezer Invoice's address, e.g. https://invoice.erezer.com; empty clears it
     * @param key       a new key from its Shop connection page; left out, the saved one is kept
     * @param removeKey true forgets the saved key
     */
    public record Change(@Size(max = 300, message = "That address is too long") String url,
                         @Size(max = 200, message = "That key is too long") String key,
                         Boolean removeKey) {
    }

    // ── Sending ─────────────────────────────────────────────────────────────

    public record SendRequest(
            @NotEmpty(message = "Choose at least one order")
            @Size(max = InvoiceAppSender.MAX_PER_SEND, message = "Send at most " + InvoiceAppSender.MAX_PER_SEND + " orders at a time")
            List<@NotNull UUID> orderIds) {
    }

    public enum Outcome {
        /** Added to the Erezer Invoice order list. */
        SENT,
        /** Erezer Invoice already had an order with this number; nothing changed there. */
        ALREADY_THERE,
        /** Not sent; {@code message} says why. */
        FAILED
    }

    /** What became of one order. {@code missingPhotos}: product photos that couldn't go with it. */
    public record SendResult(UUID orderId, String orderNumber, Outcome outcome, Long invoiceOrderId,
                             String message, int missingPhotos) {
    }

    /** The "In Invoice ✓" mark on the order list and the order page. */
    public record SentMark(Long invoiceOrderId, Instant sentAt, String sentBy, int missingPhotos) {
    }

    // ── What goes to Erezer Invoice (its ShopDtos.ShopOrder) ────────────────

    public record ShopCustomer(String name, String phone, String email, String address) {
    }

    public record ShopItem(String productName, String productCode, String description, String color, String size,
                           BigDecimal unitPrice, int quantity, String imageDescription) {
    }

    public record ShopOrder(String orderNumber, Instant orderedAt, String paymentMethod, BigDecimal advance,
                            ShopCustomer customer, BigDecimal deliveryCharge, BigDecimal discount,
                            List<ShopItem> items) {
    }
}
