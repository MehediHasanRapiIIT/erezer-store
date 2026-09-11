package kn.org.deliverybackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row of the permission catalogue. Written from {@code access.Perm} at
 * startup; grants reference it by key so a typo can never be stored.
 */
@Entity
@Table(name = "permission")
@Getter
@Setter
@NoArgsConstructor
public class PermissionDefinition {

    @Id
    @Column(name = "perm_key", length = 64)
    private String key;

    @Column(nullable = false, length = 60)
    private String area;

    @Column(nullable = false, length = 160)
    private String label;

    @Column(length = 400)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** False once the key is no longer in code; existing grants are kept but no longer shown. */
    @Column(nullable = false)
    private boolean active = true;
}
