package kn.org.deliverybackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for phone login")
public class LoginResponseDTO {
    @Schema(description = "User ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID userId;
    
    @Schema(description = "User's phone number", example = "+8801234567890")
    private String phoneNumber;
    
    @Schema(description = "Indicates if this is a new user", example = "true")
    private Boolean isNewUser;
}
