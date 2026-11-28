package kn.org.deliverybackend.integration.invoiceapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppDtos.Outcome;
import kn.org.deliverybackend.integration.invoiceapp.InvoiceAppSettingsService.Connection;
import kn.org.deliverybackend.service.FileStorageService.StoredFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

/**
 * Talks to Erezer Invoice: one order per request, its photos as files beside it
 * ({@code item-0}, {@code item-1}…), the key in {@code X-Shop-Key}.
 */
@Component
@Slf4j
public class InvoiceAppClient {

    static final String KEY_HEADER = "X-Shop-Key";

    private final RestClient http;
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public InvoiceAppClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8_000);
        factory.setReadTimeout(60_000);   // photos go up with the order
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    /** Erezer Invoice's answer about one order. */
    public record Reply(Outcome outcome, Long invoiceOrderId, String message, int missingPhotos) {
    }

    /** The key was refused: nothing more can be sent until it is fixed. */
    public static class KeyRefusedException extends RuntimeException {
        KeyRefusedException() {
            super("Erezer Invoice refused the key. Make a new key in Erezer Invoice → Shop connection and save it in Settings → Erezer Invoice.");
        }
    }

    /** Erezer Invoice couldn't be reached or answered with something unexpected. */
    public static class UnreachableException extends RuntimeException {
        UnreachableException(String message) {
            super(message);
        }
    }

    /** Checks the address and key; changes nothing there. */
    public void ping(Connection to) {
        exchange(() -> http.get()
                .uri(to.url() + "/api/integrations/shop/ping")
                .header(KEY_HEADER, to.key())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { throw failure(res.getStatusCode().value(), body(res)); })
                .toBodilessEntity());
    }

    public Reply send(Connection to, InvoiceOrderBuilder.Prepared order) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        try {
            parts.add("order", new HttpEntity<>(json.writeValueAsBytes(order.order()), jsonHeaders));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        for (int n = 0; n < order.photos().size(); n++) {
            for (StoredFile photo : order.photos().get(n)) {
                HttpHeaders fileHeaders = new HttpHeaders();
                fileHeaders.setContentType(photo.contentType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(photo.contentType()));
                ByteArrayResource file = new ByteArrayResource(photo.bytes()) {
                    @Override
                    public String getFilename() {
                        return photo.name();
                    }
                };
                parts.add("item-" + n, new HttpEntity<>(file, fileHeaders));
            }
        }

        JsonNode answer = exchange(() -> http.post()
                .uri(to.url() + "/api/integrations/shop/orders")
                .header(KEY_HEADER, to.key())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { throw failure(res.getStatusCode().value(), body(res)); })
                .body(JsonNode.class));
        if (answer == null || !answer.hasNonNull("outcome")) {
            throw new UnreachableException("Erezer Invoice gave an answer the shop didn't understand. Is the address right?");
        }
        Outcome outcome = switch (answer.get("outcome").asText()) {
            case "CREATED" -> Outcome.SENT;
            case "EXISTS" -> Outcome.ALREADY_THERE;
            default -> Outcome.FAILED;
        };
        Long id = answer.hasNonNull("orderId") ? answer.get("orderId").asLong() : null;
        String message = answer.hasNonNull("message") ? answer.get("message").asText() : null;
        return new Reply(outcome, id, message, answer.path("missingPhotos").asInt(0));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private interface Call<T> {
        T run();
    }

    private static <T> T exchange(Call<T> call) {
        try {
            return call.run();
        } catch (KeyRefusedException | UnreachableException | OrderRefusedException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new UnreachableException("Erezer Invoice couldn't be reached. Check its address in Settings → Erezer Invoice, and that it is running.");
        } catch (Exception e) {
            log.warn("Erezer Invoice call failed: {}", e.toString());
            throw new UnreachableException("Erezer Invoice gave an answer the shop didn't understand. Is the address right?");
        }
    }

    /** Erezer Invoice refused this order (its fields); the next one may still go. */
    public static class OrderRefusedException extends RuntimeException {
        OrderRefusedException(String message) {
            super(message);
        }
    }

    private RuntimeException failure(int status, String body) {
        if (status == 401) return new KeyRefusedException();
        if (status == 404) return new UnreachableException("Erezer Invoice wasn't found at that address. Check it in Settings → Erezer Invoice.");
        if (status == 400 || status == 409 || status == 413 || status == 422) {
            return new OrderRefusedException("Erezer Invoice refused it: " + reason(body));
        }
        log.warn("Erezer Invoice answered {}: {}", status, body);
        return new UnreachableException("Erezer Invoice had a problem (" + status + "). Try again in a minute.");
    }

    /** The readable part of Erezer Invoice's error: its detail, and which fields were wrong. */
    String reason(String body) {
        try {
            JsonNode node = json.readTree(body);
            StringBuilder out = new StringBuilder(node.path("detail").asText("not accepted"));
            JsonNode fields = node.path("fieldErrors");
            if (fields.isObject() && fields.size() > 0) {
                out.append(":");
                for (Iterator<Map.Entry<String, JsonNode>> it = fields.fields(); it.hasNext(); ) {
                    Map.Entry<String, JsonNode> f = it.next();
                    out.append(' ').append(f.getKey()).append(' ').append(f.getValue().asText()).append(it.hasNext() ? ";" : "");
                }
            }
            return out.toString();
        } catch (Exception e) {
            return "not accepted";
        }
    }

    private static String body(org.springframework.http.client.ClientHttpResponse res) {
        try {
            byte[] bytes = res.getBody().readNBytes(4000);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
