package kn.org.deliverybackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OtpVerificationDTO {

    private UUID id;

    private UUID userId;

    private String userRole;

    private String phoneNumber;

    private String otpCode;

    private LocalDateTime expiresAt;

    private Boolean isUsed;

    private LocalDateTime createdAt;
}
