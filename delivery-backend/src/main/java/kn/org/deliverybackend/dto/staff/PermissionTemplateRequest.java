package kn.org.deliverybackend.dto.staff;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PermissionTemplateRequest(
        @NotBlank(message = "Give the template a name") @Size(max = 100) String name,
        @NotNull(message = "Send the list of permissions") List<String> permissions) {}
