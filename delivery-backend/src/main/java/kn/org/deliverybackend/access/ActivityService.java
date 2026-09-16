package kn.org.deliverybackend.access;

import jakarta.servlet.http.HttpServletRequest;
import kn.org.deliverybackend.entity.AdminActivity;
import kn.org.deliverybackend.repository.AdminActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.HandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes the activity log: who changed what, and when. A failure here is
 * logged and swallowed, because a logging problem must never undo a change
 * the person has already made.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityService {

    private final AdminActivityRepository repository;

    public void record(StaffView staff, HttpServletRequest request, AccessRule rule, int status) {
        try {
            Object used = request.getAttribute(StaffAccess.USED_ATTRIBUTE);
            Perm perm = used instanceof Perm p ? p : rule == null ? null : rule.primary();
            String target = targetId(request);

            AdminActivity entry = new AdminActivity();
            entry.setStaffId(staff.id());
            entry.setStaffName(truncate(staff.name(), 200));
            entry.setStaffUsername(truncate(staff.username(), 150));
            entry.setMethod(request.getMethod());
            entry.setPath(truncate(request.getRequestURI(), 300));
            entry.setPermKey(perm == null ? null : perm.key());
            entry.setArea(perm == null ? areaFromPath(request.getRequestURI()) : perm.area());
            entry.setTargetId(truncate(target, 100));
            Object custom = request.getAttribute(StaffAccess.ACTIVITY_SUMMARY_ATTRIBUTE);
            entry.setSummary(truncate(custom != null ? custom.toString() : defaultSummary(request, perm, target), 400));
            Object details = request.getAttribute(StaffAccess.ACTIVITY_DETAILS_ATTRIBUTE);
            entry.setDetails(details == null ? null : truncate(details.toString(), 200_000));
            entry.setStatus(status);
            entry.setIpAddress(truncate(clientIp(request), 64));
            repository.save(entry);
        } catch (Exception e) {
            log.warn("Could not record admin activity for {} {}: {}",
                    request.getMethod(), request.getRequestURI(), e.getMessage());
        }
    }

    /** The id the action was about: the last path variable, e.g. the order in /orders/{orderId}/status. */
    @SuppressWarnings("unchecked")
    static String targetId(HttpServletRequest request) {
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(vars instanceof Map<?, ?> map) || map.isEmpty()) return null;
        List<Object> values = new ArrayList<>(((Map<String, Object>) map).values());
        Object last = values.get(values.size() - 1);
        return last == null ? null : last.toString();
    }

    private static String defaultSummary(HttpServletRequest request, Perm perm, String target) {
        String action = perm != null ? perm.label() : verb(request.getMethod()) + " " + request.getRequestURI();
        return target == null ? action : action + " · " + target;
    }

    private static String verb(String method) {
        return switch (method) {
            case "POST" -> "Created";
            case "PUT", "PATCH" -> "Updated";
            case "DELETE" -> "Deleted";
            default -> method;
        };
    }

    /** "/admin/flash-sales/…" or "/api/banners/…" → "Flash sales", "Banners", for actions with no named permission. */
    private static String areaFromPath(String uri) {
        if (uri == null) return null;
        String[] parts = uri.split("/");
        if (parts.length <= 2 || parts[2].isBlank()) return null;
        String raw = parts[2].replace('-', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private static String clientIp(HttpServletRequest request) {
        return kn.org.deliverybackend.util.ClientIp.of(request);
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
