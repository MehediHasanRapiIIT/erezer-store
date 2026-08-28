package kn.org.deliverybackend.exception;

public class UnauthorizedReviewException extends RuntimeException {
    public UnauthorizedReviewException(String message) {
        super(message);
    }
}
