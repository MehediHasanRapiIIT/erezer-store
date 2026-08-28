package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "newsletter_subscriber",
        indexes = {
                @Index(name = "idx_newsletter_status",      columnList = "status"),
                @Index(name = "idx_newsletter_unsub_token", columnList = "unsubscribe_token")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsletterSubscriber extends AbstractBaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String email;

    /** SUBSCRIBED | UNSUBSCRIBED */
    @Column(nullable = false, length = 32)
    private String status;

    @Column(length = 64)
    private String source;

    @Column(name = "unsubscribe_token", nullable = false, length = 64)
    private String unsubscribeToken;

    @Column(name = "subscribed_at", nullable = false)
    private LocalDateTime subscribedAt;

    @Column(name = "unsubscribed_at")
    private LocalDateTime unsubscribedAt;
}
