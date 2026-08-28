package kn.org.deliverybackend.service.impl;

import io.jsonwebtoken.Claims;
import kn.org.deliverybackend.dto.auth.AuthTokenResponseDTO;
import kn.org.deliverybackend.dto.auth.EmailLoginRequestDTO;
import kn.org.deliverybackend.dto.auth.RefreshTokenRequestDTO;
import kn.org.deliverybackend.dto.auth.RegisterRequestDTO;
import kn.org.deliverybackend.dto.auth.ResetPasswordRequestDTO;
import kn.org.deliverybackend.entity.Users;
import kn.org.deliverybackend.exception.DuplicateResourceException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.UsersRepository;
import kn.org.deliverybackend.security.JwtTokenProvider;
import kn.org.deliverybackend.service.CustomerAuthService;
import kn.org.deliverybackend.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerAuthServiceImpl implements CustomerAuthService {

    private static final int EMAIL_VERIFY_TTL_HOURS = 24;
    private static final int PASSWORD_RESET_TTL_MINUTES = 30;

    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;

    @Value("${app.frontend.store-url}")
    private String storeUrl;

    @Override
    @Transactional
    public AuthTokenResponseDTO register(RegisterRequestDTO request) {
        String email = normalizeEmail(request.getEmail());
        if (usersRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("An account with this email already exists.");
        }

        Users user = new Users();
        // Let @GeneratedValue assign the id; capture the persisted entity below
        // so the issued token carries the same id that lands in the DB.
        user.setEmail(email);
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setIsActive(true);
        user.setEmailVerified(false);
        user.setEmailVerificationToken(UUID.randomUUID().toString());
        user.setEmailVerificationExpiresAt(LocalDateTime.now().plusHours(EMAIL_VERIFY_TTL_HOURS));
        user = usersRepository.save(user);

        sendVerificationEmail(user);
        sendWelcomeEmail(user);

        return issueTokens(user);
    }

    @Override
    @Transactional
    public AuthTokenResponseDTO login(EmailLoginRequestDTO request) {
        String email = normalizeEmail(request.getEmail());
        Users user = usersRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password."));

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password.");
        }

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new BadCredentialsException("This account is disabled.");
        }

        user.setLastLoginAt(LocalDateTime.now());
        usersRepository.save(user);

        return issueTokens(user);
    }

    @Override
    public AuthTokenResponseDTO refresh(RefreshTokenRequestDTO request) {
        try {
            Claims claims = jwtTokenProvider.parse(request.getRefreshToken());
            if (!JwtTokenProvider.TOKEN_TYPE_REFRESH.equals(
                    claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))) {
                throw new BadCredentialsException("Not a refresh token.");
            }
            UUID userId = UUID.fromString(claims.getSubject());
            Users user = usersRepository.findById(userId)
                    .orElseThrow(() -> new BadCredentialsException("User no longer exists."));
            return issueTokens(user);
        } catch (BadCredentialsException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadCredentialsException("Invalid or expired refresh token.");
        }
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        Users user = usersRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid verification link."));

        LocalDateTime expiresAt = user.getEmailVerificationExpiresAt();
        if (expiresAt == null || expiresAt.isBefore(LocalDateTime.now())) {
            throw new ResourceNotFoundException("Verification link has expired. Please request a new one.");
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationExpiresAt(null);
        usersRepository.save(user);
    }

    @Override
    @Transactional
    public void resendVerificationEmail(String email) {
        Users user = usersRepository.findByEmail(normalizeEmail(email)).orElse(null);
        if (user == null || Boolean.TRUE.equals(user.getEmailVerified())) {
            // Don't leak whether the email exists / state — log only.
            log.info("Resend verification requested for unknown or already-verified email: {}", email);
            return;
        }
        user.setEmailVerificationToken(UUID.randomUUID().toString());
        user.setEmailVerificationExpiresAt(LocalDateTime.now().plusHours(EMAIL_VERIFY_TTL_HOURS));
        usersRepository.save(user);
        sendVerificationEmail(user);
    }

    @Override
    @Transactional
    public void requestPasswordReset(String email) {
        Users user = usersRepository.findByEmail(normalizeEmail(email)).orElse(null);
        if (user == null) {
            // Always succeed quietly — never reveal whether email exists.
            log.info("Password reset requested for unknown email: {}", email);
            return;
        }
        user.setPasswordResetToken(UUID.randomUUID().toString());
        user.setPasswordResetExpiresAt(LocalDateTime.now().plusMinutes(PASSWORD_RESET_TTL_MINUTES));
        usersRepository.save(user);

        Map<String, Object> vars = new HashMap<>();
        vars.put("firstName", user.getFirstName() != null ? user.getFirstName() : "there");
        vars.put("resetUrl", storeUrl + "/reset-password?token=" + user.getPasswordResetToken());
        vars.put("expiresInMinutes", PASSWORD_RESET_TTL_MINUTES);
        emailService.send(user.getEmail(), "Reset your Erezer password", "password-reset", vars);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequestDTO request) {
        Users user = usersRepository.findByPasswordResetToken(request.getToken())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid reset link."));

        LocalDateTime expiresAt = user.getPasswordResetExpiresAt();
        if (expiresAt == null || expiresAt.isBefore(LocalDateTime.now())) {
            throw new ResourceNotFoundException("Reset link has expired. Please request a new one.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiresAt(null);
        usersRepository.save(user);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private AuthTokenResponseDTO issueTokens(Users user) {
        String access = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String refresh = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail());
        return AuthTokenResponseDTO.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .emailVerified(Boolean.TRUE.equals(user.getEmailVerified()))
                .accessToken(access)
                .refreshToken(refresh)
                .expiresInSeconds(jwtTokenProvider.getAccessTtlSeconds())
                .tokenType("Bearer")
                .build();
    }

    private void sendVerificationEmail(Users user) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("firstName", user.getFirstName() != null ? user.getFirstName() : "there");
        vars.put("verifyUrl", storeUrl + "/verify-email?token=" + user.getEmailVerificationToken());
        vars.put("expiresInHours", EMAIL_VERIFY_TTL_HOURS);
        emailService.send(user.getEmail(), "Verify your Erezer email", "verify-email", vars);
    }

    private void sendWelcomeEmail(Users user) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("firstName", user.getFirstName() != null ? user.getFirstName() : "there");
        vars.put("storeUrl", storeUrl);
        emailService.send(user.getEmail(), "Welcome to Erezer", "welcome", vars);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
