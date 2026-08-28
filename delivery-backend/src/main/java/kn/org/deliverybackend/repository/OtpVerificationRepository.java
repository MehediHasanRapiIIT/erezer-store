package kn.org.deliverybackend.repository;

import kn.org.deliverybackend.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {
    Optional<OtpVerification> findByPhoneNumberAndOtpCodeAndIsUsedFalseAndExpiresAtAfter(
            String phoneNumber, String otpCode, LocalDateTime currentTime);

    Optional<OtpVerification> findByPhoneNumber(String phoneNumber);
}
