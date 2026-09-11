package kn.org.deliverybackend.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Any active staff member may call this endpoint, whatever their permissions.
 * For things every staff member needs, such as reading their own profile.
 * An endpoint that filters its results by permission itself also uses this.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AnyStaff {
}
