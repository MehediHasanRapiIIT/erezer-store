package kn.org.deliverybackend.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The caller's address, for rate limits and logs.
 *
 * <p>In production every request comes through Caddy, which always overwrites
 * {@code X-Real-IP} with the address it actually saw (see deploy/Caddyfile).
 * {@code X-Forwarded-For} is not used: its first entry is whatever the caller
 * typed, so reading it would let anyone pick a fresh address per request and
 * walk straight past every limit. Without a proxy (local runs) the socket
 * address is the caller.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest http) {
        String real = http.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return http.getRemoteAddr();
    }
}
