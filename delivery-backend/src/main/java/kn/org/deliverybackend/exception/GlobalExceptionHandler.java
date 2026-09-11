package kn.org.deliverybackend.exception;

import kn.org.deliverybackend.dto.response.auth.LoginResponse;
import kn.org.deliverybackend.dto.response.auth.RegistrationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Stock exceptions ──────────────────────────────────────────────────────

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientStock(
            InsufficientStockException ex) {
        log.warn("Insufficient stock: productId={}, requested={}, available={}",
                ex.getProductId(), ex.getRequested(), ex.getAvailable());
        Map<String, Object> body = new HashMap<>();
        body.put("productId", ex.getProductId());
        body.put("requested", ex.getRequested());
        body.put("available", ex.getAvailable());
        body.put("message", ex.getMessage());
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidStockOperationException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidStockOperation(
            InvalidStockOperationException ex) {
        log.warn("Invalid stock operation: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("message", ex.getMessage());
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handlePessimisticLock(
            PessimisticLockingFailureException ex) {
        log.warn("Pessimistic lock contention: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("message", "Item temporarily unavailable, please retry");
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(
            ResourceNotFoundException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", ex.getMessage());
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // ── Review exceptions ─────────────────────────────────────────────────────

    @ExceptionHandler(UnauthorizedReviewException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorizedReview(
            UnauthorizedReviewException ex) {
        log.warn("Unauthorized review attempt: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("message", ex.getMessage());
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<Map<String, Object>> handleEmailNotVerified(
            EmailNotVerifiedException ex) {
        log.warn("Email-not-verified action blocked: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("message", ex.getMessage());
        body.put("code", "EMAIL_NOT_VERIFIED");
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /** A business rule refused the request; the message says which, in plain words. */
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequest(InvalidRequestException ex) {
        log.info("Request refused by a rule: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("error", "invalid_request");
        body.put("message", ex.getMessage());
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** A service we depend on (e.g. Keycloak) failed; nothing in our database was changed for it. */
    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<Map<String, Object>> handleExternalService(ExternalServiceException ex) {
        log.warn("External service failed: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("error", "external_service");
        body.put("message", ex.getMessage());
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    /**
     * A staff member lacks a permission. Kept apart from the generic 403 so the
     * admin panel gets the missing keys and can say exactly what is missing.
     */
    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<Map<String, Object>> handlePermissionDenied(PermissionDeniedException ex) {
        log.info("Permission denied: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("error", "forbidden");
        body.put("message", ex.getMessage());
        body.put("missingPermissions", ex.getMissing());
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(ForbiddenAccessException.class)
    public ResponseEntity<Map<String, Object>> handleForbiddenAccess(
            ForbiddenAccessException ex) {
        log.warn("Forbidden access: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("message", ex.getMessage());
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateResource(
            DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("message", ex.getMessage());
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(
            org.springframework.security.authentication.BadCredentialsException ex) {
        log.warn("Bad credentials: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("message", ex.getMessage());
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitExceededException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", "Too many requests. Try again shortly.");
        body.put("retryAfterSeconds", ex.getRetryAfterSeconds());
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After",       String.valueOf(ex.getRetryAfterSeconds()))
                .header("X-RateLimit-Limit", String.valueOf(ex.getLimit()))
                .header("X-RateLimit-Window", String.valueOf(ex.getWindowSeconds()))
                .header("X-RateLimit-Reset", String.valueOf(ex.getRetryAfterSeconds()))
                .body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("errors", fieldErrors);
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // ── Auth exceptions (required by existing tests) ─────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        String path = request.getDescription(false);
        if (path.contains("/login")) {
            String msg = ex.getMessage();
            if (msg != null && msg.toLowerCase().contains("invalid credentials")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(LoginResponse.error(msg));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(LoginResponse.error(msg));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(RegistrationResponse.error(ex.getMessage()));
    }

    /**
     * A malformed request is the caller's mistake, not a server fault: an id
     * in the wrong format, a missing required parameter, or a body that
     * can't be read. Without this they fell through to the 500 below.
     */
    @ExceptionHandler({
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> handleMalformedRequest(Exception ex) {
        String message;
        if (ex instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException m) {
            String expected = m.getRequiredType() == null ? "another format" : m.getRequiredType().getSimpleName();
            message = "'" + m.getName() + "' has the wrong format (expected " + expected + ").";
        } else if (ex instanceof org.springframework.web.bind.MissingServletRequestParameterException m) {
            message = "Missing the required parameter '" + m.getParameterName() + "'.";
        } else {
            message = "The request body couldn't be read.";
        }
        log.info("Malformed request: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("error", "bad_request");
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGlobalException(Exception ex, WebRequest request) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        String path = request.getDescription(false);
        if (path.contains("/login")) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(LoginResponse.error("Internal server error"));
        }
        // Consistent {message,timestamp} body so both frontends parse errors uniformly.
        Map<String, Object> body = new HashMap<>();
        body.put("message", "Internal server error");
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
