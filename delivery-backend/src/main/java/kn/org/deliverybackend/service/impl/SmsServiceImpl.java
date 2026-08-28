package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Transactional SMS sender.
 *
 * <p>Two modes via {@code app.sms.mode}:
 * <ul>
 *   <li>{@code STUB} (default) — logs the message instead of sending. Lets dev
 *       run the full flow without a paid gateway.</li>
 *   <li>{@code REST} — sends an HTTP GET to {@code app.sms.base-url} with the
 *       params used by common Bangladeshi gateways (e.g. bulksmsbd):
 *       {@code api_key}, {@code senderid}, {@code number}, {@code message}.
 *       Adjust the param names in {@link #buildUri} to match your provider.</li>
 * </ul>
 * Failures are swallowed (logged) — SMS must never break an order.
 */
@Service
@Slf4j
public class SmsServiceImpl implements SmsService {

    @Value("${app.sms.mode:STUB}")
    private String mode;

    @Value("${app.sms.base-url:}")
    private String baseUrl;

    @Value("${app.sms.api-key:}")
    private String apiKey;

    @Value("${app.sms.sender-id:}")
    private String senderId;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public void send(String toPhone, String message) {
        if (toPhone == null || toPhone.isBlank() || message == null || message.isBlank()) {
            return;
        }
        if (!"REST".equalsIgnoreCase(mode) || baseUrl == null || baseUrl.isBlank()) {
            log.info("[SMS:STUB] -> {}: {}", toPhone, message);
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(buildUri(toPhone, message))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                log.debug("SMS sent to {} ({})", toPhone, resp.statusCode());
            } else {
                log.warn("SMS gateway returned {} for {}: {}", resp.statusCode(), toPhone, resp.body());
            }
        } catch (Exception ex) {
            log.warn("SMS send to {} failed: {}", toPhone, ex.getMessage());
        }
    }

    private URI buildUri(String toPhone, String message) {
        String sep = baseUrl.contains("?") ? "&" : "?";
        String url = baseUrl + sep
                + "api_key=" + enc(apiKey)
                + "&senderid=" + enc(senderId)
                + "&number=" + enc(toPhone)
                + "&message=" + enc(message);
        return URI.create(url);
    }

    private String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }
}
