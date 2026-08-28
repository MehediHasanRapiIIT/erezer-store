package kn.org.deliverybackend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Puts a per-request correlation id into the SLF4J MDC and echoes it back as
 * the {@code X-Correlation-Id} response header so clients (and the storefront
 * Sentry SDK) can stitch front-end errors to backend log lines.
 *
 * Also captures the authenticated principal (if any) and the client IP into
 * the MDC. Cleared in a {@code finally} so no thread-pool leak.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        String cid = request.getHeader(HEADER);
        if (cid == null || cid.isBlank() || cid.length() > 64) {
            cid = UUID.randomUUID().toString();
        }
        MDC.put("correlationId", cid);
        MDC.put("clientIp", clientIp(request));
        MDC.put("requestPath", request.getRequestURI());
        String principal = currentPrincipal();
        if (principal != null) {
            MDC.put("principal", principal);
        }
        response.setHeader(HEADER, cid);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String clientIp(HttpServletRequest http) {
        String fwd = http.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }

    private String currentPrincipal() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null && auth.isAuthenticated() ? auth.getName() : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
