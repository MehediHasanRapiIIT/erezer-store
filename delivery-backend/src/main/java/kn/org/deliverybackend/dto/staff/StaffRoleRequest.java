package kn.org.deliverybackend.dto.staff;

import jakarta.validation.constraints.NotNull;
import kn.org.deliverybackend.entity.StaffMember;

public record StaffRoleRequest(@NotNull(message = "Choose a role") StaffMember.Role role) {}
