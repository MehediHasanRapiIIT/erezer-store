package kn.org.deliverybackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for phone login")
public class LoginRequestDTO {
    @Schema(description = "User's phone number", example = "+8801234567890", required = true)
    private String phoneNumber;
}
