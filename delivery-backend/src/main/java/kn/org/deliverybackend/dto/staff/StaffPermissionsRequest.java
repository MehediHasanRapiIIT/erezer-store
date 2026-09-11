package kn.org.deliverybackend.dto.staff;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** The complete new set of permission keys for a moderator; replaces what they had. */
public record StaffPermissionsRequest(@NotNull(message = "Send the list of permissions") List<String> permissions) {}
