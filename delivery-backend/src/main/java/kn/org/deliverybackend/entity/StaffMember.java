package kn.org.deliverybackend.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A person who can open the admin panel. Keycloak owns their login; this row
 * owns what they may do. Timestamps are UTC wall-clock, like the rest of the
 * schema.
 */
@Entity
@Table(name = "staff_member")
@Getter
@Setter
@NoArgsConstructor
public class StaffMember {

    public enum Role { ADMIN, MODERATOR }

    @Id
    private UUID id;

    @Column(name = "keycloak_user_id", nullable = false, unique = true, length = 64)
    private String keycloakUserId;

    @Column(nullable = false, length = 150)
    private String username;

    @Column(length = 255)
    private String email;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 150)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Permission keys granted to a moderator. Ignored for admins, who may do everything. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "staff_permission", joinColumns = @JoinColumn(name = "staff_id"))
    @Column(name = "perm_key", length = 64)
    private Set<String> permissions = new HashSet<>();

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** Best available display name, for the activity log and the header. */
    public String displayName() {
        if (fullName != null && !fullName.isBlank()) return fullName.trim();
        if (username != null && !username.isBlank()) return username;
        return email != null ? email : "unknown";
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
