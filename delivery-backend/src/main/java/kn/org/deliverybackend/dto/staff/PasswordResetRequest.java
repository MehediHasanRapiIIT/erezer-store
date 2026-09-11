package kn.org.deliverybackend.dto.staff;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank(message = "A temporary password is required")
        @Size(min = 8, max = 100, message = "The temporary password needs 8 to 100 characters")
        String temporaryPassword) {}
