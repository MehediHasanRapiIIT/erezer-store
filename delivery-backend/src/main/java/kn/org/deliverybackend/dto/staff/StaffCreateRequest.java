package kn.org.deliverybackend.dto.staff;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kn.org.deliverybackend.entity.StaffMember;

import java.util.List;

/** Adds a person to the staff: a new Keycloak login plus their permissions. Role defaults to moderator. */
public record StaffCreateRequest(
        @NotBlank(message = "Full name is required") @Size(max = 200) String fullName,
        @NotBlank(message = "Username is required")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{3,50}$",
                message = "Use 3 to 50 letters, digits, dots, dashes or underscores")
        String username,
        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") String email,
        @NotBlank(message = "A temporary password is required")
        @Size(min = 8, max = 100, message = "The temporary password needs 8 to 100 characters")
        String temporaryPassword,
        StaffMember.Role role,
        List<String> permissions) {}
