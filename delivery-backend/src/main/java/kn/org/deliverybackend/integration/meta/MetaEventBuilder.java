package kn.org.deliverybackend.integration.meta;

import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.OrderItem;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Builds Meta Conversions API event payloads. Pure functions with no I/O, so
 * the exact bytes Meta receives can be unit-tested.
 *
 * <p>Personal data is normalised the way Meta's matching expects and then
 * SHA-256 hashed here; only hashes leave the server. IP address, user agent
 * and the pixel cookies are sent in clear text, as Meta requires.
 */
public final class MetaEventBuilder {

    /** Must match the storefront pixel's `eventID` for the same order, so Meta deduplicates. */
    public static String purchaseEventId(UUID orderId) {
        return "purchase-" + orderId;
    }

    /**
     * The Purchase event for one order.
     *
     * @param productNames product id → name, for the readable `content_name`
     * @param storeUrl     public storefront URL, for `event_source_url`
     */
    public static Map<String, Object> purchase(Order order,
                                               List<OrderItem> items,
                                               Map<Long, String> productNames,
                                               RequestAttribution attribution,
                                               String storeUrl,
                                               Instant when) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_name", "Purchase");
        event.put("event_time", when.getEpochSecond());
        event.put("event_id", purchaseEventId(order.getId()));
        event.put("event_source_url", trimSlash(storeUrl) + "/orders/" + order.getId());
        event.put("action_source", "website");
        event.put("user_data", userData(order, attribution));
        event.put("custom_data", purchaseData(order, items, productNames));
        return event;
    }

    // ── user_data ───────────────────────────────────────────────────────────

    static Map<String, Object> userData(Order order, RequestAttribution attribution) {
        Map<String, Object> user = new LinkedHashMap<>();
        putHashed(user, "em", normaliseEmail(order.getCustomerEmail()));
        putHashed(user, "ph", normalisePhone(order.getCustomerPhone()));
        String[] name = splitName(order.getCustomerName());
        putHashed(user, "fn", name[0]);
        putHashed(user, "ln", name[1]);
        if (order.getClientId() != null) {
            putHashed(user, "external_id", order.getClientId().toString());
        }
        putHashed(user, "country", "bd");
        if (attribution != null) {
            if (attribution.clientIp() != null) user.put("client_ip_address", attribution.clientIp());
            if (attribution.userAgent() != null) user.put("client_user_agent", attribution.userAgent());
            if (attribution.fbp() != null) user.put("fbp", attribution.fbp());
            if (attribution.fbc() != null) user.put("fbc", attribution.fbc());
        }
        return user;
    }

    private static void putHashed(Map<String, Object> target, String key, String normalised) {
        if (normalised == null || normalised.isBlank()) return;
        target.put(key, List.of(sha256(normalised)));
    }

    // ── custom_data ─────────────────────────────────────────────────────────

    static Map<String, Object> purchaseData(Order order, List<OrderItem> items, Map<Long, String> productNames) {
        List<String> ids = new ArrayList<>();
        List<Map<String, Object>> contents = new ArrayList<>();
        int units = 0;
        for (OrderItem item : items) {
            if (item.getProductId() == null) continue;
            String id = String.valueOf(item.getProductId());
            int qty = item.getQuantity() == null ? 0 : item.getQuantity();
            ids.add(id);
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("id", id);
            line.put("quantity", qty);
            line.put("item_price", money(item.getPriceAtOrder()));
            String name = productNames.get(item.getProductId());
            if (name != null) line.put("title", name);
            contents.add(line);
            units += qty;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("currency", "BDT");
        data.put("value", money(order.getTotalAmount()));
        data.put("content_type", "product");
        data.put("content_ids", ids);
        data.put("contents", contents);
        data.put("num_items", units);
        data.put("order_id", order.getId().toString());
        return data;
    }

    // ── normalisation, per Meta's matching rules ────────────────────────────

    /** Lower-case, trimmed. */
    public static String normaliseEmail(String raw) {
        if (raw == null) return null;
        String email = raw.trim().toLowerCase(Locale.ROOT);
        return email.isEmpty() ? null : email;
    }

    /** Digits only with the country code: "01712-345678" becomes "8801712345678". */
    public static String normalisePhone(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.isEmpty()) return null;
        if (digits.startsWith("880")) return digits;
        if (digits.startsWith("0")) digits = digits.substring(1);
        return "880" + digits;
    }

    /** "Rahim Ahmed" → ["rahim", "ahmed"]; a single word is the first name. */
    static String[] splitName(String raw) {
        if (raw == null || raw.isBlank()) return new String[]{null, null};
        String[] parts = raw.trim().toLowerCase(Locale.ROOT).split("\\s+", 2);
        return new String[]{parts[0], parts.length > 1 ? parts[1] : null};
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static double money(BigDecimal value) {
        return value == null ? 0d : value.doubleValue();
    }

    private static String trimSlash(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private MetaEventBuilder() {}
}
