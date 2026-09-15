package kn.org.deliverybackend.util;

import jakarta.servlet.http.HttpServletRequest;

/** The caller's address for rate limits; behind the proxy that is the first X-Forwarded-For entry. */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest http) {
        String fwd = http.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
