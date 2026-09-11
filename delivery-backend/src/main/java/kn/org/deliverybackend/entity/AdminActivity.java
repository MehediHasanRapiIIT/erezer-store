package kn.org.deliverybackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * One change made in the admin panel. The person is stored by name as well as
 * id, with no foreign key, so their history survives them being deleted.
 */
@Entity
@Table(name = "admin_activity")
@Getter
@Setter
@NoArgsConstructor
public class AdminActivity {

    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "staff_id")
    private UUID staffId;

    @Column(name = "staff_name", nullable = false, length = 200)
    private String staffName;

    @Column(name = "staff_username", length = 150)
    private String staffUsername;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = 300)
    private String path;

    @Column(name = "perm_key", length = 64)
    private String permKey;

    @Column(length = 60)
    private String area;

    @Column(name = "target_id", length = 100)
    private String targetId;

    @Column(length = 400)
    private String summary;

    private Integer status;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (occurredAt == null) occurredAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
