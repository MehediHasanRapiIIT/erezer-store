package kn.org.deliverybackend.exception;

/**
 * A service the backend depends on (such as Keycloak, the login system)
 * could not be reached or refused the request. Mapped to HTTP 502 with the
 * message, which says what could not be done.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }
}
