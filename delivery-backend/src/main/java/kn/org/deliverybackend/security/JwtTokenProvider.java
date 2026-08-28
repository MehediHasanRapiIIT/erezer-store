package kn.org.deliverybackend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Spring-issued JWT for customer (storefront) sessions. Keycloak is still used
 * for admin auth; this provider only signs/validates customer tokens.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";
    public static final String CLAIM_TOKEN_TYPE = "tt";

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-token-ttl-minutes:60}")
    private long accessTtlMinutes;

    @Value("${app.jwt.refresh-token-ttl-days:14}")
    private long refreshTtlDays;

    @Value("${app.jwt.issuer:erezer-store}")
    private String issuer;

    private SecretKey key;

    @PostConstruct
    void init() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes (256 bits) for HS256");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String generateAccessToken(UUID userId, String email) {
        return buildToken(userId, email, TOKEN_TYPE_ACCESS, accessTtlMinutes * 60);
    }

    public String generateRefreshToken(UUID userId, String email) {
        return buildToken(userId, email, TOKEN_TYPE_REFRESH, refreshTtlDays * 24 * 60 * 60);
    }

    private String buildToken(UUID userId, String email, String type, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("email", email)
                .claim(CLAIM_TOKEN_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .id(UUID.randomUUID().toString())
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        Jws<Claims> jws = Jwts.parser()
                .requireIssuer(issuer)
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }

    public boolean isValidAccessToken(String token) {
        try {
            Claims c = parse(token);
            return TOKEN_TYPE_ACCESS.equals(c.get(CLAIM_TOKEN_TYPE, String.class));
        } catch (Exception ex) {
            log.debug("Invalid access token: {}", ex.getMessage());
            return false;
        }
    }

    public long getAccessTtlSeconds() {
        return accessTtlMinutes * 60;
    }
}
