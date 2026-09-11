package kn.org.deliverybackend.dto.staff;

import java.util.List;
import java.util.UUID;

/**
 * A staff member as shown on the Staff page. Times are business-local
 * ("yyyy-MM-dd HH:mm", Asia/Dhaka). {@code permissions} is empty for admins,
 * who may do everything; {@code you} marks the person viewing the list.
 */
public record StaffDTO(UUID id,
                       String username,
                       String email,
                       String fullName,
                       String name,
                       String role,
                       boolean active,
                       String lastSeenAt,
                       String createdAt,
                       String createdBy,
                       List<String> permissions,
                       boolean you) {}
