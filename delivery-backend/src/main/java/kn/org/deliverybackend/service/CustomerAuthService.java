package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.auth.AuthTokenResponseDTO;
import kn.org.deliverybackend.dto.auth.EmailLoginRequestDTO;
import kn.org.deliverybackend.dto.auth.RefreshTokenRequestDTO;
import kn.org.deliverybackend.dto.auth.RegisterRequestDTO;
import kn.org.deliverybackend.dto.auth.ResetPasswordRequestDTO;

public interface CustomerAuthService {

    AuthTokenResponseDTO register(RegisterRequestDTO request);

    AuthTokenResponseDTO login(EmailLoginRequestDTO request);

    AuthTokenResponseDTO refresh(RefreshTokenRequestDTO request);

    void verifyEmail(String token);

    void resendVerificationEmail(String email);

    void requestPasswordReset(String email);

    void resetPassword(ResetPasswordRequestDTO request);
}
