package kn.org.deliverybackend.dto.staff;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A staff member's name and email, kept in step with their Keycloak login. */
public record StaffUpdateRequest(
        @NotBlank(message = "Full name is required") @Size(max = 200) String fullName,
        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") String email) {}
