package kn.org.deliverybackend.access;

import kn.org.deliverybackend.exception.PermissionDeniedException;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;

/**
 * The staff member making the current admin request, for checks that depend
 * on the data sent rather than on the endpoint alone, such as changing a
 * price inside a product edit. Populated by {@link PermissionInterceptor}.
 */
public final class StaffAccess {

    public static final String STAFF_ATTRIBUTE = "erezer.access.staff";
    public static final String RULE_ATTRIBUTE = "erezer.access.rule";
    /** Set by a controller to replace the generic activity log line for this request. */
    public static final String ACTIVITY_SUMMARY_ATTRIBUTE = "erezer.access.summary";
    /** Set by a controller to keep longer detail with the activity log line. */
    public static final String ACTIVITY_DETAILS_ATTRIBUTE = "erezer.access.details";

    private StaffAccess() {}

    public static Optional<StaffView> current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) return Optional.empty();
        Object staff = attributes.getAttribute(STAFF_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return staff instanceof StaffView view ? Optional.of(view) : Optional.empty();
    }

    /** True for an admin, or a moderator holding this permission. False outside an admin request. */
    public static boolean has(Perm perm) {
        return current().map(staff -> staff.has(perm)).orElse(false);
    }

    /** The permission a request actually used, when its endpoint accepts several. */
    public static final String USED_ATTRIBUTE = "erezer.access.used";

    /** Refuse the request with 403 unless the current staff member holds the permission. */
    public static void require(Perm perm) {
        if (!has(perm)) throw new PermissionDeniedException(perm);
        markUsed(perm);
    }

    /** Names the permission this request exercised, so the activity log shows the real action. */
    public static void markUsed(Perm perm) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null && perm != null) {
            attributes.setAttribute(USED_ATTRIBUTE, perm, RequestAttributes.SCOPE_REQUEST);
        }
    }

    /** A clearer line for the activity log than the one derived from the endpoint. */
    public static void describe(String summary) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null && summary != null) {
            attributes.setAttribute(ACTIVITY_SUMMARY_ATTRIBUTE, summary, RequestAttributes.SCOPE_REQUEST);
        }
    }

    /** Longer detail kept with the activity log line, shown under "Show details". */
    public static void details(String details) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null && details != null && !details.isBlank()) {
            attributes.setAttribute(ACTIVITY_DETAILS_ATTRIBUTE, details, RequestAttributes.SCOPE_REQUEST);
        }
    }
}
