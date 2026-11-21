package kn.org.deliverybackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** A phone running the Erezer app, by its Firebase token; the customer logged in on it, if any. */
@Entity
@Table(name = "push_device")
@Getter
@Setter
@NoArgsConstructor
public class PushDevice {

    @Id
    @Column(length = 512)
    private String token;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public PushDevice(String token) {
        this.token = token;
    }
}
