package kn.org.deliverybackend.exception;

/**
 * Thrown when a customer with an unverified email address attempts an action
 * that requires a verified account (e.g. placing an order). Mapped to HTTP 403.
 */
public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
