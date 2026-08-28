package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.auth.AuthTokenResponseDTO;
import kn.org.deliverybackend.dto.auth.EmailLoginRequestDTO;
import kn.org.deliverybackend.dto.auth.ForgotPasswordRequestDTO;
import kn.org.deliverybackend.dto.auth.MessageResponseDTO;
import kn.org.deliverybackend.dto.auth.RefreshTokenRequestDTO;
import kn.org.deliverybackend.dto.auth.RegisterRequestDTO;
import kn.org.deliverybackend.dto.auth.ResetPasswordRequestDTO;
import kn.org.deliverybackend.dto.auth.VerifyEmailRequestDTO;
import kn.org.deliverybackend.service.CustomerAuthService;
import kn.org.deliverybackend.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/app/auth")
@RequiredArgsConstructor
@Tag(name = "Customer Authentication", description = "Email + password auth for the Erezer storefront")
public class CustomerAuthController {

    private final CustomerAuthService authService;
    private final RateLimiterService rateLimiter;

    @PostMapping("/register")
    @Operation(summary = "Register a new customer account")
    public ResponseEntity<AuthTokenResponseDTO> register(
            @Valid @RequestBody RegisterRequestDTO request,
            HttpServletRequest http) {
        enforceRateLimit("register", http);
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with email + password")
    public ResponseEntity<AuthTokenResponseDTO> login(
            @Valid @RequestBody EmailLoginRequestDTO request,
            HttpServletRequest http) {
        enforceRateLimit("login", http);
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access token")
    public ResponseEntity<AuthTokenResponseDTO> refresh(
            @Valid @RequestBody RefreshTokenRequestDTO request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Confirm an email-verification link token")
    public ResponseEntity<MessageResponseDTO> verifyEmail(
            @Valid @RequestBody VerifyEmailRequestDTO request) {
        authService.verifyEmail(request.getToken());
        return ResponseEntity.ok(MessageResponseDTO.of("Email verified."));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Re-send the email-verification link")
    public ResponseEntity<MessageResponseDTO> resendVerification(
            @Valid @RequestBody ForgotPasswordRequestDTO request,
            HttpServletRequest http) {
        enforceRateLimit("resend-verify", http);
        authService.resendVerificationEmail(request.getEmail());
        return ResponseEntity.ok(MessageResponseDTO.of(
                "If an unverified account exists for this email, a new verification link has been sent."));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password-reset email")
    public ResponseEntity<MessageResponseDTO> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDTO request,
            HttpServletRequest http) {
        enforceRateLimit("forgot", http);
        authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(MessageResponseDTO.of(
                "If an account exists for this email, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Apply a new password using a reset-token")
    public ResponseEntity<MessageResponseDTO> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDTO request,
            HttpServletRequest http) {
        enforceRateLimit("reset", http);
        authService.resetPassword(request);
        return ResponseEntity.ok(MessageResponseDTO.of("Password updated. You can now sign in."));
    }

    @PostMapping("/logout")
    @Operation(summary = "Stateless logout — clients should discard the JWT")
    public ResponseEntity<MessageResponseDTO> logout() {
        return ResponseEntity.ok(MessageResponseDTO.of("Signed out."));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private void enforceRateLimit(String bucket, HttpServletRequest http) {
        String key = "auth:" + bucket + ":" + clientIp(http);
        if (!rateLimiter.tryAcquireAuth(key)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many attempts. Please try again later.");
        }
    }

    private String clientIp(HttpServletRequest http) {
        String fwd = http.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
