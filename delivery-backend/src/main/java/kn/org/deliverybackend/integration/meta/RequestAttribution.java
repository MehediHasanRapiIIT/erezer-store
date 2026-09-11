package kn.org.deliverybackend.integration.meta;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * What Meta needs to tie a server-side event to the same shopper and the same
 * ad click as the browser pixel: the shopper's IP and browser, plus the two
 * cookies the pixel sets, which the storefront forwards as headers because the
 * API lives on a different domain.
 *
 * <p>Must be captured on the request thread, before any hand-off to an async
 * executor, because it reads the current HTTP request.
 */
public record RequestAttribution(String clientIp, String userAgent, String fbp, String fbc) {

    public static final RequestAttribution NONE = new RequestAttribution(null, null, null, null);

    /** Snapshot of the current HTTP request, or {@link #NONE} outside one. */
    public static RequestAttribution capture() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return NONE;
        }
        HttpServletRequest request = attrs.getRequest();
        return new RequestAttribution(
                clientIp(request),
                blankToNull(request.getHeader("User-Agent")),
                blankToNull(request.getHeader("X-Meta-Fbp")),
                blankToNull(request.getHeader("X-Meta-Fbc")));
    }

    /** Behind Caddy or any proxy the real address is the first X-Forwarded-For entry. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return blankToNull(request.getRemoteAddr());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
