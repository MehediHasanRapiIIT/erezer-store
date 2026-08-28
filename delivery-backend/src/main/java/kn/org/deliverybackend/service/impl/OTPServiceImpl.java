package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.*;
import kn.org.deliverybackend.entity.OtpVerification;
import kn.org.deliverybackend.entity.Users;
import kn.org.deliverybackend.exception.DuplicateResourceException;
import kn.org.deliverybackend.exception.ResourceNotFoundException;
import kn.org.deliverybackend.repository.OtpVerificationRepository;
import kn.org.deliverybackend.repository.UsersRepository;
import kn.org.deliverybackend.service.OTPService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OTPServiceImpl implements OTPService {

    private final OtpVerificationRepository otpVerificationRepository;
    private final UsersRepository usersRepository;

    private static final int OTP_LENGTH = 6;
    private static final int OTP_EXPIRY_MINUTES = 10;

    @Override
    public void generateOTP(OTPRequestDTO request) {
        String phoneNumber = request.getPhoneNumber();

        // Check if user exists
        Users existingUser = usersRepository.findByPhoneNumber(phoneNumber)
                .orElse(null);

        // Generate OTP
        String otpCode = generateOTPCode();

        // Check if OTP verification already exists
        OtpVerification otpVerification = otpVerificationRepository.findByPhoneNumber(phoneNumber)
                .orElse(new OtpVerification());

        //otpVerification.setId(otpVerification.getId() != null ? otpVerification.getId() : UUID.randomUUID());
        otpVerification.setPhoneNumber(phoneNumber);
        otpVerification.setOtpCode(otpCode);
        otpVerification.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        otpVerification.setIsUsed(false);

        if (existingUser != null) {
            otpVerification.setUserId(existingUser.getId());
            otpVerification.setUserRole("CONSUMER");
        }

        otpVerificationRepository.save(otpVerification);
        log.info("Generated OTP {} for phone number {}", otpCode, phoneNumber);
    }

    @Override
    @Transactional
    public OTPVerifyResponseDTO verifyOTP(OTPVerifyRequestDTO request) {
        String phoneNumber = request.getPhoneNumber();
        String otpCode = request.getOtpCode();

        // Find active OTP verification
        OtpVerification otpVerification = otpVerificationRepository.findByPhoneNumberAndOtpCodeAndIsUsedFalseAndExpiresAtAfter(
                phoneNumber, otpCode, LocalDateTime.now())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired OTP"));

        // Check if OTP is expired
        if (otpVerification.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResourceNotFoundException("OTP has expired");
        }

        // Mark OTP as used
        otpVerification.setIsUsed(true);
        otpVerificationRepository.save(otpVerification);

        // Find or create user
        Users user = usersRepository.findByPhoneNumber(phoneNumber)
                .orElseGet(() -> {
                    Users newUser = new Users();
                    newUser.setId(UUID.randomUUID());
                    newUser.setPhoneNumber(phoneNumber);
                    newUser.setIsActive(true);
                    return usersRepository.save(newUser);
                });

        // Generate token (simplified - in production use JWT)
        String token = generateToken(user.getId());

        return new OTPVerifyResponseDTO(
                user.getId(),
                user.getPhoneNumber(),
                token,
                user.getCreatedAt() != null && user.getCreatedAt().equals(user.getUpdatedAt())
        );
    }

    private String generateOTPCode() {
        Random random = new Random();
        StringBuilder otp = new StringBuilder(OTP_LENGTH);
        for (int i = 0; i < OTP_LENGTH; i++) {
            otp.append(random.nextInt(10));
        }
        return otp.toString();
    }

    private String generateToken(UUID userId) {
        return UUID.randomUUID().toString() + "-" + userId.toString();
    }
}
