package kn.org.deliverybackend.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The permission a staff member needs to call this admin endpoint. Admins
 * always pass; moderators need the permission granted on the Staff page.
 *
 * <p>Every admin endpoint must carry this, {@link AdminOnly} or
 * {@link AnyStaff}. A test fails the build if one is missing, so a new
 * endpoint can never be accidentally open to every moderator.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresPermission {

    /** One or more permissions. */
    Perm[] value();

    /** {@code ALL}: every listed permission. {@code ANY}: at least one of them. */
    Mode mode() default Mode.ALL;

    enum Mode { ALL, ANY }
}
