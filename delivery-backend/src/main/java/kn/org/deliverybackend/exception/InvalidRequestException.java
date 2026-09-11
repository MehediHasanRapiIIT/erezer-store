package kn.org.deliverybackend.exception;

/**
 * A request that breaks a business rule, such as deleting your own staff
 * account or removing the last admin. Mapped to HTTP 400 with the message,
 * which is written for the person using the admin panel.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
