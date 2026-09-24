package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.settings.MetaPixelChangeDTO;
import kn.org.deliverybackend.dto.settings.MetaPixelSettingsDTO;
import kn.org.deliverybackend.entity.StoreSettings;
import kn.org.deliverybackend.exception.ExternalServiceException;
import kn.org.deliverybackend.exception.InvalidRequestException;
import kn.org.deliverybackend.repository.StoreSettingsRepository;
import kn.org.deliverybackend.service.MetaPixelSettingsService;
import kn.org.deliverybackend.util.SecretBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Meta Pixel settings, saved in the database.
 *
 * <p>Anything the owner saves here wins. Until they save something, the values in
 * the server's own configuration are used, so a shop set up before this screen
 * existed keeps working untouched.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MetaPixelSettingsServiceImpl implements MetaPixelSettingsService {

    private final StoreSettingsRepository repository;
    private final SecretBox secretBox;
    private final RestClient restClient = RestClient.create();

    @Value("${app.meta.pixel-id:}")
    private String filePixelId;

    @Value("${app.meta.capi-access-token:}")
    private String fileAccessToken;

    @Value("${app.meta.test-event-code:}")
    private String fileTestEventCode;

    @Value("${app.meta.capi-base-url:https://graph.facebook.com}")
    private String baseUrl;

    @Value("${app.meta.api-version:v23.0}")
    private String apiVersion;

    @Value("${app.frontend.store-url}")
    private String storeUrl;

    @Override
    @Transactional(readOnly = true)
    public MetaPixelSettingsDTO get() {
        StoreSettings settings = row();
        String savedToken = secretBox.unlock(settings == null ? null : settings.getMetaCapiToken());
        MetaCredentials live = resolve(settings);
        boolean fromAdmin = settings != null && notBlank(settings.getMetaPixelId());

        return MetaPixelSettingsDTO.builder()
                .enabled(settings == null || !Boolean.FALSE.equals(settings.getMetaEnabled()))
                .pixelId(settings == null ? null : blankToNull(settings.getMetaPixelId()))
                .tokenSaved(notBlank(savedToken))
                .tokenHint(SecretBox.hint(savedToken))
                .testEventCode(settings == null ? null : blankToNull(settings.getMetaTestEventCode()))
                .source(live.browserPixelOn() ? (fromAdmin ? "admin" : "server file") : "not set")
                .serverReporting(live.serverReportingOn())
                .build();
    }

    @Override
    @Transactional
    public MetaPixelSettingsDTO update(MetaPixelChangeDTO change) {
        StoreSettings settings = repository.findById(StoreSettings.SINGLETON_ID)
                .orElseThrow(() -> new InvalidRequestException("Store settings are not ready yet."));

        if (change.getEnabled() != null) settings.setMetaEnabled(change.getEnabled());
        if (change.getPixelId() != null) settings.setMetaPixelId(blankToNull(change.getPixelId().trim()));
        if (change.getTestEventCode() != null) {
            settings.setMetaTestEventCode(blankToNull(change.getTestEventCode().trim()));
        }
        if (Boolean.TRUE.equals(change.getRemoveToken())) {
            settings.setMetaCapiToken(null);
        } else if (notBlank(change.getAccessToken())) {
            settings.setMetaCapiToken(secretBox.lock(change.getAccessToken().trim()));
        }
        repository.save(settings);
        return get();
    }

    @Override
    @Transactional(readOnly = true)
    public MetaCredentials credentials() {
        return resolve(row());
    }

    @Override
    @Transactional(readOnly = true)
    public String sendTestEvent() {
        MetaCredentials live = resolve(row());
        if (!live.browserPixelOn()) {
            throw new InvalidRequestException("Add your Pixel ID first.");
        }
        if (!live.serverReportingOn()) {
            throw new InvalidRequestException("Add your Conversions API access token first.");
        }

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_name", "PageView");
        event.put("event_time", Instant.now().getEpochSecond());
        event.put("event_id", "erezer-test-" + Instant.now().toEpochMilli());
        event.put("event_source_url", storeUrl);
        event.put("action_source", "website");
        event.put("user_data", Map.of("client_user_agent", "Erezer admin test"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", List.of(event));
        if (notBlank(live.testEventCode())) body.put("test_event_code", live.testEventCode());

        try {
            String reply = restClient.post()
                    .uri(trimSlash(baseUrl) + "/" + apiVersion + "/" + live.pixelId() + "/events?access_token={t}",
                            live.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            log.info("Meta test event accepted: {}", reply);
            return notBlank(live.testEventCode())
                    ? "Meta accepted the test event. Open Events Manager → Test events to see it."
                    : "Meta accepted the test event. Add a test event code above if you want to watch it arrive.";
        } catch (Exception ex) {
            // Meta answers with a readable reason: a wrong id, an expired token, no permission.
            throw new ExternalServiceException("Meta refused the test event: " + reason(ex.getMessage()));
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private StoreSettings row() {
        return repository.findById(StoreSettings.SINGLETON_ID).orElse(null);
    }

    /** Saved settings win; the server's configuration is the fallback. */
    private MetaCredentials resolve(StoreSettings settings) {
        if (settings != null && Boolean.FALSE.equals(settings.getMetaEnabled())) {
            return new MetaCredentials(null, null, null);
        }
        String pixelId = settings != null && notBlank(settings.getMetaPixelId())
                ? settings.getMetaPixelId().trim() : blankToNull(filePixelId);
        String token = settings != null && notBlank(settings.getMetaCapiToken())
                ? secretBox.unlock(settings.getMetaCapiToken()) : blankToNull(fileAccessToken);
        String testCode = settings != null && notBlank(settings.getMetaTestEventCode())
                ? settings.getMetaTestEventCode().trim() : blankToNull(fileTestEventCode);
        return new MetaCredentials(pixelId, token, testCode);
    }

    /** The message Meta sent back, without the HTTP noise around it. */
    private static String reason(String raw) {
        if (raw == null) return "no reason given.";
        int start = raw.indexOf("\"message\":\"");
        if (start < 0) return raw.length() > 200 ? raw.substring(0, 200) : raw;
        int from = start + "\"message\":\"".length();
        int end = raw.indexOf('"', from);
        return end > from ? raw.substring(from, end) : raw.substring(from);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return notBlank(value) ? value : null;
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
