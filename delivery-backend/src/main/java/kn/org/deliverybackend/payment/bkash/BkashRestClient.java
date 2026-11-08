package kn.org.deliverybackend.payment.bkash;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kn.org.deliverybackend.exception.InvalidRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Real bKash Tokenized Checkout, "without agreement": the customer types their wallet
 * number, OTP and PIN on bKash's own page every time. Used whenever bKash credentials
 * are set (or app.bkash.mode=REST); without them the shop runs {@link BkashSandboxStubClient}.
 *
 * <p>Both versions bKash hands out are spoken, chosen by the base URL:
 * <ul>
 *   <li>V2 ({@code https://tokenized.pay.bka.sh/v2}), as in "PGW Tokenized Payment V2 (Non-Beta)":
 *       {@code /tokenized-checkout/auth/grant-token}, {@code /tokenized-checkout/payment/create|execute},
 *       {@code /tokenized-checkout/query/payment}; ids spelt {@code paymentId}, {@code trxId};
 *       errors as {@code externalCode} + {@code errorMessageEn}.</li>
 *   <li>v1.2.0-beta ({@code https://tokenized.sandbox.bka.sh/v1.2.0-beta}):
 *       {@code /tokenized/checkout/token/grant}, {@code /tokenized/checkout/create|execute},
 *       {@code /tokenized/checkout/payment/status}; ids spelt {@code paymentID}, {@code trxID};
 *       errors as {@code statusCode} other than 0000 + {@code statusMessage}.</li>
 * </ul>
 *
 * <p>bKash's rules followed here: the token is asked for once and kept for its hour
 * (grant is not repeated per payment, and refresh is never called more than twice an
 * hour); every call gives up after 30 seconds; when Execute gives no answer, Query
 * Payment decides; and bKash's own message is shown to the customer unchanged.
 */
@Component
@Slf4j
@ConditionalOnExpression("'${app.bkash.mode:}' == 'REST' or ('${app.bkash.mode:}' == '' and '${app.bkash.app-key:}' != '')")
public class BkashRestClient implements BkashPaymentService {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    /** A new token this long before the old one runs out. */
    private static final Duration EARLY = Duration.ofMinutes(5);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final String appKey;
    private final String appSecret;
    private final String username;
    private final String password;
    private final boolean v2;
    private final RestClient http;

    private String token;
    private String refreshToken;
    private Instant tokenUntil = Instant.EPOCH;
    /** When the refreshes of the last hour were made: bKash blocks a third within the hour. */
    private final java.util.Deque<Instant> refreshes = new java.util.ArrayDeque<>();

    public BkashRestClient(@Value("${app.bkash.base-url}") String baseUrl,
                           @Value("${app.bkash.app-key}") String appKey,
                           @Value("${app.bkash.app-secret}") String appSecret,
                           @Value("${app.bkash.username}") String username,
                           @Value("${app.bkash.password}") String password) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.username = username;
        this.password = password;
        this.v2 = !this.baseUrl.contains("beta");
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(TIMEOUT);
        this.http = RestClient.builder().requestFactory(factory).build();
        log.info("[bKash] Real checkout ({}) at {}", v2 ? "V2" : "v1.2.0-beta", this.baseUrl);
    }

    @Override
    public BkashPaymentResponse createPayment(BkashCreatePaymentRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (!v2) body.put("mode", "0011");
        body.put("payerReference", request.getPayerReference());
        body.put("callbackURL", request.getCallbackUrl());
        body.put("amount", request.getAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        body.put("currency", "BDT");
        body.put("intent", "sale");
        body.put("merchantInvoiceNumber", request.getInvoiceNumber());
        JsonNode reply = call(v2 ? "/tokenized-checkout/payment/create" : "/tokenized/checkout/create", body);
        String paymentId = text(reply, "paymentId", "paymentID");
        String url = text(reply, "bkashURL", "bKashURL");
        if (paymentId == null || url == null) {
            throw new InvalidRequestException(errorOf(reply, "bKash could not start the payment. Please try again."));
        }
        return response(reply).toBuilder().paymentId(paymentId).bkashURL(url).status("Initiated").build();
    }

    @Override
    public BkashPaymentResponse executePayment(String paymentId) {
        try {
            JsonNode reply = call(v2 ? "/tokenized-checkout/payment/execute" : "/tokenized/checkout/execute", idBody(paymentId));
            String status = text(reply, "transactionStatus");
            if (status == null) {
                // Already executed (a second visit to the callback): what bKash holds is the answer.
                log.info("[bKash] Execute {} answered {}; asking Query Payment", paymentId, errorOf(reply, ""));
                BkashPaymentResponse known = queryPayment(paymentId);
                if ("Completed".equalsIgnoreCase(known.getStatus())) return known;
                return failed(paymentId, errorOf(reply, "The payment was not completed."));
            }
            return response(reply).toBuilder().paymentId(paymentId).build();
        } catch (ResourceAccessException noAnswer) {
            // No answer from Execute: bKash says ask Query Payment before deciding.
            log.warn("[bKash] Execute {} got no answer ({}); asking Query Payment", paymentId, noAnswer.getMessage());
            return queryPayment(paymentId);
        }
    }

    @Override
    public BkashPaymentResponse queryPayment(String paymentId) {
        JsonNode reply = call(v2 ? "/tokenized-checkout/query/payment" : "/tokenized/checkout/payment/status", idBody(paymentId));
        String status = text(reply, "transactionStatus");
        if (status == null) return failed(paymentId, errorOf(reply, "bKash could not find this payment."));
        return response(reply).toBuilder().paymentId(paymentId).build();
    }

    @Override
    public BkashRefundResponse refund(String paymentId, String trxId, BigDecimal amount, String sku, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        String money = amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        if (v2) {
            body.put("paymentId", paymentId);
            body.put("refundAmount", money);
            body.put("trxId", trxId);
        } else {
            body.put("paymentID", paymentId);
            body.put("amount", money);
            body.put("trxID", trxId);
        }
        body.put("sku", sku);
        body.put("reason", reason);
        JsonNode reply;
        try {
            reply = call(v2 ? "/tokenized-checkout/refund/payment/transaction" : "/tokenized/checkout/payment/refund", body);
        } catch (ResourceAccessException noAnswer) {
            log.warn("[bKash] Refund of {} got no answer: {}", paymentId, noAnswer.getMessage());
            return BkashRefundResponse.builder().status("Unknown")
                    .errorMessage("bKash did not answer. Check the bKash merchant account before trying again.").build();
        }
        String status = text(reply, "refundTransactionStatus", "transactionStatus");
        String refundTrx = text(reply, "refundTrxId", "refundTrxID");
        String refunded = text(reply, "refundAmount", "amount");
        if (!"Completed".equalsIgnoreCase(status)) {
            return BkashRefundResponse.builder().status(status == null ? "Failed" : status)
                    .errorMessage(errorOf(reply, "bKash did not make the refund.")).build();
        }
        return BkashRefundResponse.builder().status("Completed").refundTrxId(refundTrx)
                .amount(refunded == null ? amount : new BigDecimal(refunded)).build();
    }

    // ── calls ─────────────────────────────────────────────────────────────────

    private Map<String, Object> idBody(String paymentId) {
        return Map.of(v2 ? "paymentId" : "paymentID", paymentId);
    }

    /** One call with the token; bKash's error replies are read like any other reply. */
    private JsonNode call(String path, Object body) {
        String auth = token();
        return send(path, body, Map.of("Authorization", auth, "X-App-Key", appKey));
    }

    /** One POST to bKash, written to the bKash API log both ways (secrets hidden). */
    private JsonNode send(String path, Object body, Map<String, String> headers) {
        long n = BkashApiLog.request(baseUrl + path, headers, body);
        long start = System.nanoTime();
        try {
            var request = http.post().uri(baseUrl + path)
                    .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON);
            headers.forEach(request::header);
            var reply = request.body(body).retrieve().toEntity(String.class);
            BkashApiLog.answer(n, reply.getStatusCode().value(), (System.nanoTime() - start) / 1_000_000, reply.getBody());
            return read(reply.getBody());
        } catch (RestClientResponseException e) {
            BkashApiLog.answer(n, e.getStatusCode().value(), (System.nanoTime() - start) / 1_000_000, e.getResponseBodyAsString());
            return read(e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            BkashApiLog.noAnswer(n, (System.nanoTime() - start) / 1_000_000, e.getMessage());
            throw e;
        }
    }

    /** The token for the next call: kept for its hour, refreshed near the end, granted anew if need be. */
    private synchronized String token() {
        Instant now = Instant.now();
        if (token != null && now.isBefore(tokenUntil)) return token;
        while (!refreshes.isEmpty() && refreshes.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) refreshes.pollFirst();
        JsonNode reply = null;
        if (refreshToken != null && refreshes.size() < 2) {
            refreshes.addLast(now);
            reply = tokenCall(v2 ? "/tokenized-checkout/auth/refresh-token" : "/tokenized/checkout/token/refresh",
                    Map.of("app_key", appKey, "app_secret", appSecret, "refresh_token", refreshToken));
            if (text(reply, "id_token") == null) reply = null;
        }
        if (reply == null) {
            reply = tokenCall(v2 ? "/tokenized-checkout/auth/grant-token" : "/tokenized/checkout/token/grant",
                    Map.of("app_key", appKey, "app_secret", appSecret));
        }
        String id = text(reply, "id_token");
        if (id == null) {
            log.error("[bKash] No token: {}", reply);
            throw new InvalidRequestException("bKash payment is not available right now. Please choose another payment method.");
        }
        token = id;
        refreshToken = text(reply, "refresh_token");
        long seconds = reply.path("expires_in").asLong(3600);
        tokenUntil = now.plusSeconds(seconds).minus(EARLY);
        return token;
    }

    private JsonNode tokenCall(String path, Object body) {
        try {
            return send(path, body, Map.of("username", username, "password", password));
        } catch (ResourceAccessException e) {
            log.error("[bKash] {} unreachable: {}", path, e.getMessage());
            throw new InvalidRequestException("bKash payment is not available right now. Please choose another payment method.");
        }
    }

    // ── replies ───────────────────────────────────────────────────────────────

    private static JsonNode read(String raw) {
        try {
            return raw == null || raw.isBlank() ? JSON.createObjectNode() : JSON.readTree(raw);
        } catch (Exception e) {
            return JSON.createObjectNode();
        }
    }

    /** The first of these fields that has a value; null if none does. */
    static String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode v = node.get(name);
            if (v != null && !v.isNull() && !v.asText().isBlank()) return v.asText();
        }
        return null;
    }

    /** bKash's own words for what went wrong, as it asks merchants to show them. */
    static String errorOf(JsonNode reply, String fallback) {
        String message = text(reply, "errorMessageEn", "statusMessage", "errorMessage", "message");
        if (message == null || "Successful".equalsIgnoreCase(message)) return fallback;
        return message;
    }

    private static BkashPaymentResponse response(JsonNode reply) {
        String amount = text(reply, "amount");
        return BkashPaymentResponse.builder()
                .status(text(reply, "transactionStatus"))
                .trxId(text(reply, "trxId", "trxID"))
                .amount(amount == null ? null : new BigDecimal(amount))
                .currency(text(reply, "currency"))
                .merchantInvoiceNumber(text(reply, "merchantInvoiceNumber", "merchantInvoice"))
                .payerAccount(text(reply, "payerAccount", "customerMsisdn"))
                .build();
    }

    private static BkashPaymentResponse failed(String paymentId, String message) {
        return BkashPaymentResponse.builder().status("Failed").paymentId(paymentId).errorMessage(message).build();
    }
}
