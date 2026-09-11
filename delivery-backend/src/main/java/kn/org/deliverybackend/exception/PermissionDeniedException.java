package kn.org.deliverybackend.exception;

import kn.org.deliverybackend.access.Perm;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A staff member tried something they have not been given. Mapped to HTTP 403
 * with the missing permission keys, so the admin panel can explain what is
 * missing instead of treating it as a logout.
 */
public class PermissionDeniedException extends ForbiddenAccessException {

    private final List<String> missing;

    public PermissionDeniedException(Perm... missing) {
        super(message(missing));
        this.missing = Arrays.stream(missing).map(Perm::key).toList();
    }

    public PermissionDeniedException(String message) {
        super(message);
        this.missing = List.of();
    }

    public List<String> getMissing() {
        return missing;
    }

    private static String message(Perm... missing) {
        if (missing.length == 0) return "You don't have permission to do this.";
        return "You don't have permission for: "
                + Arrays.stream(missing).map(Perm::label).collect(Collectors.joining(", ")) + ".";
    }
}
