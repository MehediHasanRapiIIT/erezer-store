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
        enforceEmailLimit("login", request.getEmail(), 10, java.time.Duration.ofMinutes(15));
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access token")
    public ResponseEntity<AuthTokenResponseDTO> refresh(
            @Valid @RequestBody RefreshTokenRequestDTO request,
            HttpServletRequest http) {
        rateLimiter.enforce("auth:refresh:" + clientIp(http), 60, java.time.Duration.ofMinutes(1));
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Confirm an email-verification link token")
    public ResponseEntity<MessageResponseDTO> verifyEmail(
            @Valid @RequestBody VerifyEmailRequestDTO request,
            HttpServletRequest http) {
        enforceRateLimit("verify-email", http);
        authService.verifyEmail(request.getToken());
        return ResponseEntity.ok(MessageResponseDTO.of("Email verified."));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Re-send the email-verification link")
    public ResponseEntity<MessageResponseDTO> resendVerification(
            @Valid @RequestBody ForgotPasswordRequestDTO request,
            HttpServletRequest http) {
        enforceRateLimit("resend-verify", http);
        enforceEmailLimit("resend-verify", request.getEmail(), 3, java.time.Duration.ofHours(1));
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
        enforceEmailLimit("forgot", request.getEmail(), 3, java.time.Duration.ofHours(1));
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
        rateLimiter.enforceAuth("auth:" + bucket + ":" + clientIp(http));
    }

    /**
     * Per account as well as per address: guessing one customer's password from
     * many addresses, or flooding one inbox with reset/verification emails.
     */
    private void enforceEmailLimit(String bucket, String email, int max, java.time.Duration window) {
        String who = email == null ? "" : email.trim().toLowerCase();
        rateLimiter.enforce("auth:" + bucket + ":email:" + who, max, window);
    }

    private String clientIp(HttpServletRequest http) {
        return kn.org.deliverybackend.util.ClientIp.of(http);
    }
}
