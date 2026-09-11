package kn.org.deliverybackend.access;

import kn.org.deliverybackend.entity.StaffMember;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An immutable snapshot of a staff member and what they may do, taken once per
 * request. Admins hold every permission, now and in future versions.
 */
public record StaffView(UUID id,
                        String keycloakUserId,
                        String username,
                        String email,
                        String name,
                        StaffMember.Role role,
                        boolean active,
                        Set<String> permissions) {

    private static final Set<String> ALL_KEYS =
            Arrays.stream(Perm.values()).map(Perm::key).collect(Collectors.toUnmodifiableSet());

    public boolean isAdmin() {
        return role == StaffMember.Role.ADMIN;
    }

    public boolean has(Perm perm) {
        return isAdmin() || permissions.contains(perm.key());
    }

    /** Keys this person actually holds: every key for an admin. */
    public Set<String> effectivePermissions() {
        return isAdmin() ? ALL_KEYS : permissions;
    }
}
