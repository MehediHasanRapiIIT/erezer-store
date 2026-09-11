package kn.org.deliverybackend.access;

import kn.org.deliverybackend.exception.PermissionDeniedException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

import java.util.Arrays;

/**
 * What an admin endpoint requires, read from its annotations. The method's
 * own annotation wins over the controller's. No annotation at all means
 * admin-only, so a forgotten endpoint fails closed.
 */
public record AccessRule(Kind kind, Perm[] perms, RequiresPermission.Mode mode) {

    public enum Kind { ADMIN_ONLY, ANY_STAFF, PERMISSIONS }

    private static final AccessRule ADMIN = new AccessRule(Kind.ADMIN_ONLY, new Perm[0], RequiresPermission.Mode.ALL);
    private static final AccessRule STAFF = new AccessRule(Kind.ANY_STAFF, new Perm[0], RequiresPermission.Mode.ALL);

    public static AccessRule of(HandlerMethod handler) {
        AccessRule onMethod = read(handler.getMethod());
        if (onMethod != null) return onMethod;
        AccessRule onType = read(handler.getBeanType());
        return onType != null ? onType : ADMIN;
    }

    /** True when an endpoint carries an explicit rule; used by the deny-by-default test. */
    public static boolean isDeclared(java.lang.reflect.Method method, Class<?> type) {
        return read(method) != null || read(type) != null;
    }

    private static AccessRule read(java.lang.reflect.AnnotatedElement element) {
        if (AnnotatedElementUtils.hasAnnotation(element, AdminOnly.class)) return ADMIN;
        if (AnnotatedElementUtils.hasAnnotation(element, AnyStaff.class)) return STAFF;
        RequiresPermission rp = AnnotatedElementUtils.findMergedAnnotation(element, RequiresPermission.class);
        return rp == null ? null : new AccessRule(Kind.PERMISSIONS, rp.value(), rp.mode());
    }

    /** Refuse with 403 unless this moderator meets the rule. Admins are never passed here. */
    public void check(StaffView staff) {
        switch (kind) {
            case ADMIN_ONLY -> throw new PermissionDeniedException("Only an admin can do this.");
            case ANY_STAFF -> { }
            case PERMISSIONS -> {
                if (mode == RequiresPermission.Mode.ANY) {
                    if (Arrays.stream(perms).noneMatch(staff::has)) throw new PermissionDeniedException(perms);
                } else {
                    Perm[] missing = Arrays.stream(perms).filter(p -> !staff.has(p)).toArray(Perm[]::new);
                    if (missing.length > 0) throw new PermissionDeniedException(missing);
                }
            }
        }
    }

    /**
     * The permission that names this action, for the activity log. None when
     * any of several would do; the endpoint then marks the one it used.
     */
    public Perm primary() {
        if (perms.length == 0 || (mode == RequiresPermission.Mode.ANY && perms.length > 1)) return null;
        return perms[0];
    }
}
