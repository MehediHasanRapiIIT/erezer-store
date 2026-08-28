package kn.org.deliverybackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO for user information")
public class UsersDTO {
    @Schema(description = "User ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "User's phone number", example = "+8801234567890")
    private String phoneNumber;

    @Schema(description = "Indicates if user is active", example = "true")
    private Boolean isActive;

    @Schema(description = "User's first name", example = "John")
    private String firstName;

    @Schema(description = "User's last name", example = "Doe")
    private String lastName;

    @Schema(description = "User's email", example = "john.doe@example.com")
    private String email;

    @Schema(description = "User's profile image URL", example = "https://example.com/images/profile.jpg")
    private String profileImage;

    private Float latitude;

    private Float longitude;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Schema(description = "List of user addresses")
    private List<AddressesDTO> addresses;
}
