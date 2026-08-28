package kn.org.deliverybackend.service;

import kn.org.deliverybackend.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Lightweight Redis-backed fixed-window rate limiter.
 *
 * <p>Two call shapes:
 * <ul>
 *   <li>{@link #tryAcquireAuth(String)} — boolean check; legacy callers may
 *       translate {@code false} to a {@link RateLimitExceededException} themselves.</li>
 *   <li>{@link #enforceAuth(String)} — preferred. Throws
 *       {@link RateLimitExceededException} with retry hints so the global
 *       exception handler can attach a {@code Retry-After} header.</li>
 * </ul>
 *
 * <p>The limiter is <b>fail-open</b>: a Redis outage logs WARN but lets the
 * request through. This prefers availability over a misconfigured DoS-proof
 * deny-all when Redis is down.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate redis;

    @Value("${app.rate-limit.auth.requests-per-minute:10}")
    private int authMaxRequests;

    @Value("${app.rate-limit.auth.window-seconds:60}")
    private int authWindowSeconds;

    // ── Boolean API (legacy / fine-grained) ────────────────────────────────────

    public boolean tryAcquireAuth(String scopedKey) {
        return tryAcquire("rl:" + scopedKey, authMaxRequests, Duration.ofSeconds(authWindowSeconds));
    }

    public boolean tryAcquire(String key, int maxRequests, Duration window) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null) {
                return true;
            }
            if (count == 1L) {
                redis.expire(key, window);
            }
            return count <= maxRequests;
        } catch (Exception ex) {
            log.warn("Rate limiter unavailable, allowing request through: {}", ex.getMessage());
            return true;
        }
    }

    // ── Throwing API (preferred) ───────────────────────────────────────────────

    /**
     * Same as {@link #tryAcquireAuth(String)} but throws
     * {@link RateLimitExceededException} with the remaining window (in seconds)
     * when blocked. Caller doesn't need to know about Retry-After plumbing.
     */
    public void enforceAuth(String scopedKey) {
        String key = "rl:" + scopedKey;
        if (!tryAcquire(key, authMaxRequests, Duration.ofSeconds(authWindowSeconds))) {
            int retry = remainingSeconds(key, authWindowSeconds);
            throw new RateLimitExceededException(retry, authMaxRequests, authWindowSeconds);
        }
    }

    private int remainingSeconds(String key, int fallback) {
        try {
            Long ttl = redis.getExpire(key);
            if (ttl == null || ttl <= 0) return fallback;
            return ttl.intValue();
        } catch (Exception ex) {
            return fallback;
        }
    }
}
