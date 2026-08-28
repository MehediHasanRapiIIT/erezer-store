package kn.org.deliverybackend.exception;

/**
 * Thrown by {@link kn.org.deliverybackend.service.RateLimiterService}-using
 * endpoints when a client exceeds the configured request budget. Carries the
 * retry hint so the global exception handler can attach a {@code Retry-After}
 * header.
 */
public class RateLimitExceededException extends RuntimeException {

    private final int retryAfterSeconds;
    private final int limit;
    private final int windowSeconds;

    public RateLimitExceededException(int retryAfterSeconds, int limit, int windowSeconds) {
        super("Rate limit exceeded — retry after " + retryAfterSeconds + "s");
        this.retryAfterSeconds = retryAfterSeconds;
        this.limit = limit;
        this.windowSeconds = windowSeconds;
    }

    public int getRetryAfterSeconds() { return retryAfterSeconds; }
    public int getLimit()             { return limit; }
    public int getWindowSeconds()     { return windowSeconds; }
}
