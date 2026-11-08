package kn.org.deliverybackend.payment.bkash;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Every call to bKash and its answer, as bKash asks merchants to keep for their review:
 * one line for the request (address, headers, body) and one for the answer (HTTP status,
 * time taken, body), paired by a number. Secrets never appear: the password, app secret,
 * username and tokens are written as ***, the app key and wallet numbers only in part.
 *
 * <p>Logger name {@code bkash.api}. To collect them:
 * {@code docker logs erezer-backend 2>&1 | grep "bKash API"}.
 */
final class BkashApiLog {

    private static final Logger LOG = LoggerFactory.getLogger("bkash.api");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicLong NUMBER = new AtomicLong();

    /** Never written, whatever their value. */
    private static final Set<String> SECRET = Set.of("app_secret", "password", "username", "id_token", "refresh_token",
            "authorization", "token");
    /** Shown only in part. */
    private static final Set<String> PARTLY = Set.of("app_key", "x-app-key");
    /** Wallet numbers: the middle hidden, as on the invoice. */
    private static final Set<String> WALLET = Set.of("payerreference", "payeraccount", "customermsisdn");

    private BkashApiLog() {
    }

    /** Writes the request; answers its number, for the matching answer line. */
    static long request(String url, Map<String, String> headers, Object body) {
        long n = NUMBER.incrementAndGet();
        Map<String, String> shown = new LinkedHashMap<>();
        headers.forEach((k, v) -> shown.put(k, hide(k, v)));
        LOG.info("[bKash API] #{} → POST {} headers={} body={}", n, url, shown, masked(JSON.valueToTree(body)));
        return n;
    }

    static void answer(long n, int status, long millis, String raw) {
        LOG.info("[bKash API] #{} ← HTTP {} in {} ms body={}", n, status, millis, maskedRaw(raw));
    }

    static void noAnswer(long n, long millis, String why) {
        LOG.warn("[bKash API] #{} ← no answer after {} ms: {}", n, millis, why);
    }

    private static String maskedRaw(String raw) {
        if (raw == null || raw.isBlank()) return "(empty)";
        try {
            return masked(JSON.readTree(raw));
        } catch (Exception e) {
            return raw.length() > 500 ? raw.substring(0, 500) + "…" : raw;
        }
    }

    static String masked(JsonNode node) {
        if (node == null) return "null";
        JsonNode copy = node.deepCopy();
        if (copy instanceof ObjectNode object) {
            var names = new java.util.ArrayList<String>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode v = object.get(name);
                if (v != null && v.isValueNode()) object.put(name, hide(name, v.asText()));
            }
        }
        return copy.toString();
    }

    static String hide(String name, String value) {
        if (value == null) return null;
        String key = name.toLowerCase();
        if (SECRET.contains(key)) return "***";
        if (PARTLY.contains(key)) return value.length() <= 4 ? "***" : value.substring(0, 4) + "***";
        if (WALLET.contains(key) && value.matches("\\d{7,}")) {
            return value.substring(0, 3) + "*".repeat(value.length() - 6) + value.substring(value.length() - 3);
        }
        return value;
    }
}
