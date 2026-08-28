package kn.org.deliverybackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for OTP verification")
public class OTPVerifyRequestDTO {
    @Schema(description = "User's phone number", example = "+8801234567890", required = true)
    private String phoneNumber;
    
    @Schema(description = "OTP code received via SMS", example = "123456", required = true)
    private String otpCode;
}
