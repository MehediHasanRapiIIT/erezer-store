package kn.org.deliverybackend.access;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kn.org.deliverybackend.exception.PermissionDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Runs before every admin action. Security has already confirmed the Keycloak
 * login; this decides whether that person may do this particular thing, then
 * records successful changes in the activity log.
 *
 * <p>Only requests authenticated by Keycloak are checked. Storefront and
 * public requests on the same paths (reading products, customer reviews) pass
 * straight through.
 */
@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {

    private static final Set<String> CHANGES = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final StaffDirectory directory;
    private final ActivityService activity;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (!(handler instanceof HandlerMethod method)) return true;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken keycloakLogin)) return true;

        StaffView staff = directory.resolve(keycloakLogin.getToken()).orElseThrow(() ->
                new PermissionDeniedException(
                        "This login is not set up for the admin panel. Ask an admin to add you on the Staff page."));
        if (!staff.active()) {
            throw new PermissionDeniedException("This staff account has been deactivated.");
        }

        AccessRule rule = AccessRule.of(method);
        request.setAttribute(StaffAccess.STAFF_ATTRIBUTE, staff);
        request.setAttribute(StaffAccess.RULE_ATTRIBUTE, rule);

        if (!staff.isAdmin()) rule.check(staff);
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                @Nullable Exception ex) {
        if (ex != null || !CHANGES.contains(request.getMethod())) return;
        if (!(request.getAttribute(StaffAccess.STAFF_ATTRIBUTE) instanceof StaffView staff)) return;
        int status = response.getStatus();
        if (status >= 400) return;
        AccessRule rule = request.getAttribute(StaffAccess.RULE_ATTRIBUTE) instanceof AccessRule r ? r : null;
        activity.record(staff, request, rule, status);
    }
}
