package kn.org.deliverybackend.exception;

/**
 * Thrown when an authenticated customer tries to act on a resource that isn't
 * theirs (e.g. another user's returns). Mapped to HTTP 403.
 */
public class ForbiddenAccessException extends RuntimeException {
    public ForbiddenAccessException(String message) {
        super(message);
    }
}
