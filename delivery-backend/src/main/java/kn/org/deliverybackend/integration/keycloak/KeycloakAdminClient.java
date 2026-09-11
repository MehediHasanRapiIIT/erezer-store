package kn.org.deliverybackend.integration.keycloak;

import kn.org.deliverybackend.entity.StaffMember;
import kn.org.deliverybackend.exception.DuplicateResourceException;
import kn.org.deliverybackend.exception.ExternalServiceException;
import kn.org.deliverybackend.exception.InvalidRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages staff logins in Keycloak from the Staff page: create, update,
 * disable, sign out, reset password, delete, and switch between the realm
 * roles {@code admin} and {@code moderator}.
 *
 * <p>Acts as the service account of the confidential client
 * {@code erezer-backend-admin}, which {@code deploy/keycloak_setup.py}
 * creates with the user-management roles and whose secret it writes to .env.
 * No person ever logs in with it.
 */
@Component
@Slf4j
public class KeycloakAdminClient {

    static final String ROLE_ADMIN = "admin";
    static final String ROLE_MODERATOR = "moderator";
    private static final Pattern KEYCLOAK_MESSAGE =
            Pattern.compile("\"(?:errorMessage|error_description)\"\\s*:\\s*\"([^\"]+)\"");

    private final RestClient http = RestClient.create();
    private final String serverUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    private String token;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public KeycloakAdminClient(@Value("${app.keycloak.admin.server-url:http://localhost:9090}") String serverUrl,
                               @Value("${app.keycloak.admin.realm:delivery-admin}") String realm,
                               @Value("${app.keycloak.admin.client-id:erezer-backend-admin}") String clientId,
                               @Value("${app.keycloak.admin.client-secret:}") String clientSecret) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
    }

    public record NewLogin(String username, String email, String fullName) {}

    /**
     * Creates an enabled login with a temporary password, which Keycloak makes
     * the person replace at their first login. Returns the new Keycloak user id.
     */
    public String createUser(NewLogin login, String temporaryPassword) {
        Map<String, Object> body = profile(login.email(), login.fullName());
        body.put("username", login.username());
        body.put("enabled", true);
        body.put("credentials", List.of(temporaryPassword(temporaryPassword)));
        ResponseEntity<Void> created = call("create the login", () -> http.post().uri(adminUrl("/users"))
                .headers(this::auth).contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toBodilessEntity());
        URI location = created.getHeaders().getLocation();
        if (location == null) {
            throw new ExternalServiceException("The login system created the account but didn't say which one it is.");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    public void updateProfile(String userId, String email, String fullName) {
        call("update the login", () -> http.put().uri(adminUrl("/users/" + userId)).headers(this::auth)
                .contentType(MediaType.APPLICATION_JSON).body(profile(email, fullName))
                .retrieve().toBodilessEntity());
    }

    public void setEnabled(String userId, boolean enabled) {
        call(enabled ? "enable the login" : "disable the login", () -> http.put().uri(adminUrl("/users/" + userId))
                .headers(this::auth).contentType(MediaType.APPLICATION_JSON).body(Map.of("enabled", enabled))
                .retrieve().toBodilessEntity());
    }

    /** Ends every session of this person, so they must log in again (or can't, if disabled). */
    public void signOutEverywhere(String userId) {
        call("sign the person out", () -> http.post().uri(adminUrl("/users/" + userId + "/logout"))
                .headers(this::auth).retrieve().toBodilessEntity());
    }

    /** Replaces the password with a temporary one that must be changed at the next login. */
    public void setTemporaryPassword(String userId, String temporaryPassword) {
        call("reset the password", () -> http.put().uri(adminUrl("/users/" + userId + "/reset-password"))
                .headers(this::auth).contentType(MediaType.APPLICATION_JSON)
                .body(temporaryPassword(temporaryPassword)).retrieve().toBodilessEntity());
    }

    /** Deletes the login. A login that is already gone counts as deleted. */
    public void deleteUser(String userId) {
        call("delete the login", () -> {
            try {
                return http.delete().uri(adminUrl("/users/" + userId)).headers(this::auth)
                        .retrieve().toBodilessEntity();
            } catch (HttpClientErrorException.NotFound alreadyGone) {
                return null;
            }
        });
    }

    /** For undoing a half-finished action: deletes the login and only logs a failure. */
    public void deleteUserQuietly(String userId) {
        try {
            deleteUser(userId);
        } catch (RuntimeException e) {
            log.warn("Could not remove Keycloak login {} while undoing a failed change: {}", userId, e.getMessage());
        }
    }

    /**
     * False only when Keycloak says this login no longer exists. Used before
     * creating a staff row on first login, so a deleted person's still-valid
     * token can't bring them back. Any doubt (not configured, unreachable)
     * answers true: this check must never lock out a real login.
     */
    public boolean loginExists(String userId) {
        if (clientSecret.isBlank()) return true;
        try {
            http.get().uri(adminUrl("/users/" + userId)).headers(this::auth).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound gone) {
            return false;
        } catch (RuntimeException e) {
            log.warn("Could not check Keycloak login {}: {}", userId, e.getMessage());
            return true;
        }
    }

    /** Gives the login the realm role for this staff role and takes the other one away. */
    public void setStaffRole(String userId, StaffMember.Role role) {
        String grant = role == StaffMember.Role.ADMIN ? ROLE_ADMIN : ROLE_MODERATOR;
        String revoke = role == StaffMember.Role.ADMIN ? ROLE_MODERATOR : ROLE_ADMIN;
        String mappings = adminUrl("/users/" + userId + "/role-mappings/realm");
        List<Map<?, ?>> granted = List.of(realmRole(grant));
        call("give the login its role", () -> http.post().uri(mappings).headers(this::auth)
                .contentType(MediaType.APPLICATION_JSON).body(granted).retrieve().toBodilessEntity());
        List<Map<?, ?>> revoked = List.of(realmRole(revoke));
        call("take the old role away", () -> http.method(HttpMethod.DELETE).uri(mappings).headers(this::auth)
                .contentType(MediaType.APPLICATION_JSON).body(revoked).retrieve().toBodilessEntity());
    }

    // ── internals ───────────────────────────────────────────────────────────

    private Map<?, ?> realmRole(String name) {
        return call("look up the " + name + " role", () -> http.get().uri(adminUrl("/roles/" + name))
                .headers(this::auth).retrieve().body(Map.class));
    }

    private static Map<String, Object> profile(String email, String fullName) {
        String name = fullName == null ? "" : fullName.trim();
        int space = name.indexOf(' ');
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("email", email);
        profile.put("emailVerified", true);
        profile.put("firstName", space < 0 ? name : name.substring(0, space));
        profile.put("lastName", space < 0 ? "" : name.substring(space + 1).trim());
        return profile;
    }

    private static Map<String, Object> temporaryPassword(String value) {
        return Map.of("type", "password", "value", value, "temporary", true);
    }

    private String adminUrl(String path) {
        return serverUrl + "/admin/realms/" + realm + path;
    }

    private void auth(HttpHeaders headers) {
        headers.setBearerAuth(token());
    }

    private synchronized String token() {
        if (clientSecret.isBlank()) {
            throw new ExternalServiceException("Staff logins can't be managed yet: the backend has no Keycloak client "
                    + "secret. Run deploy/keycloak_setup.py, then restart the backend.");
        }
        if (token != null && Instant.now().isBefore(tokenExpiresAt)) return token;
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        Map<?, ?> body;
        try {
            body = http.post().uri(serverUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
        } catch (RestClientResponseException e) {
            log.warn("Keycloak refused the backend's client credentials: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ExternalServiceException("The login system refused the backend's credentials. "
                    + "Check KEYCLOAK_ADMIN_CLIENT_SECRET, or run deploy/keycloak_setup.py again.");
        } catch (ResourceAccessException e) {
            throw unreachable();
        }
        if (body == null || !(body.get("access_token") instanceof String accessToken)) {
            throw new ExternalServiceException("The login system answered without a token.");
        }
        long lifetime = body.get("expires_in") instanceof Number n ? n.longValue() : 60;
        token = accessToken;
        tokenExpiresAt = Instant.now().plusSeconds(Math.max(10, lifetime - 30));
        return token;
    }

    private <T> T call(String what, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 409) {
                throw new DuplicateResourceException("That username or email is already used by another login.");
            }
            if (status == 400) {
                throw new InvalidRequestException("The login system rejected this: " + keycloakMessage(e) + ".");
            }
            if (status == 401) {
                synchronized (this) { token = null; }
            }
            log.warn("Keycloak refused to {}: {} {}", what, status, e.getResponseBodyAsString());
            throw new ExternalServiceException("The login system couldn't " + what + " (HTTP " + status + ").");
        } catch (ResourceAccessException e) {
            throw unreachable();
        }
    }

    private static String keycloakMessage(RestClientResponseException e) {
        Matcher m = KEYCLOAK_MESSAGE.matcher(e.getResponseBodyAsString());
        return m.find() ? m.group(1) : "invalid data";
    }

    private static ExternalServiceException unreachable() {
        return new ExternalServiceException("Couldn't reach the login system (Keycloak). Try again in a moment.");
    }
}
