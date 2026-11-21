package kn.org.deliverybackend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import kn.org.deliverybackend.repository.PushDeviceRepository;
import kn.org.deliverybackend.service.PushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Sends app notifications through Firebase Cloud Messaging (the HTTP v1 API).
 * The Firebase service account comes from FIREBASE_SERVICE_ACCOUNT: the JSON
 * key itself, base64 of it, or a path to the file. Without it, every
 * notification is only written to the log ("[PUSH:STUB]"), like the SMS stub.
 */
@Slf4j
@Service
public class FirebasePushService implements PushService {

    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final PushDeviceRepository devices;
    private final ObjectMapper json = new ObjectMapper();
    private final RestClient http = RestClient.create();
    private final GoogleCredentials credentials;
    private final String projectId;

    public FirebasePushService(PushDeviceRepository devices,
                               @Value("${app.push.firebase-service-account:}") String serviceAccount) {
        this.devices = devices;
        GoogleCredentials creds = null;
        String project = null;
        if (serviceAccount != null && !serviceAccount.isBlank()) {
            try {
                byte[] key = readKey(serviceAccount.trim());
                project = json.readTree(key).path("project_id").asText(null);
                creds = GoogleCredentials.fromStream(new ByteArrayInputStream(key)).createScoped(List.of(SCOPE));
            } catch (Exception e) {
                log.warn("[push] Firebase service account could not be read; notifications only go to the log: {}",
                        e.getMessage());
            }
        }
        this.credentials = project == null ? null : creds;
        this.projectId = project;
        log.info("[push] App notifications {}", credentials == null ? "OFF (log only)" : "ON for Firebase project " + projectId);
    }

    /** The key as given: JSON, base64 of the JSON, or a path to the file. */
    private static byte[] readKey(String value) throws Exception {
        if (value.startsWith("{")) return value.getBytes(StandardCharsets.UTF_8);
        Path file = Path.of(value);
        if (Files.isRegularFile(file)) return Files.readAllBytes(file);
        return Base64.getDecoder().decode(value);
    }

    @Override
    public void send(Collection<String> tokens, String title, String body, Map<String, String> data) {
        for (String token : new LinkedHashSet<>(tokens)) {
            if (token == null || token.isBlank()) continue;
            if (credentials == null) {
                log.info("[PUSH:STUB] -> {}…: {} | {}", token.substring(0, Math.min(12, token.length())), title, body);
                continue;
            }
            sendOne(token, title, body, data);
        }
    }

    private void sendOne(String token, String title, String body, Map<String, String> data) {
        try {
            credentials.refreshIfExpired();
            String accessToken = credentials.getAccessToken().getTokenValue();
            Map<String, Object> message = Map.of("message", Map.of(
                    "token", token,
                    "notification", Map.of("title", title, "body", body),
                    "data", data,
                    // "orders" is the app's "Order updates" channel.
                    "android", Map.of("priority", "HIGH", "notification", Map.of("channel_id", "orders"))));
            http.post()
                    .uri("https://fcm.googleapis.com/v1/projects/{project}/messages:send", projectId)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(message)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            // The app was removed or the phone reset: Firebase no longer knows this one.
            if (e.getStatusCode().value() == 404 || isUnregistered(e)) {
                devices.deleteById(token);
                log.info("[push] Forgot a phone Firebase no longer knows");
            } else {
                log.warn("[push] Firebase refused a notification: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            }
        } catch (Exception e) {
            log.warn("[push] Notification not sent: {}", e.getMessage());
        }
    }

    private boolean isUnregistered(HttpClientErrorException e) {
        try {
            JsonNode err = json.readTree(e.getResponseBodyAsString()).path("error");
            return err.toString().contains("UNREGISTERED");
        } catch (Exception ignored) {
            return false;
        }
    }
}
