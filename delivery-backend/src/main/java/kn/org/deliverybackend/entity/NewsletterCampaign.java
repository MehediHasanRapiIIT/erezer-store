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
@Table(name = "newsletter_campaign",
        indexes = {
                @Index(name = "idx_campaign_status", columnList = "status, created_at DESC")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsletterCampaign extends AbstractBaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(name = "body_html", nullable = false, columnDefinition = "TEXT")
    private String bodyHtml;

    /** ALL_SUBSCRIBERS | REGISTERED_CUSTOMERS */
    @Column(nullable = false, length = 64)
    private String audience;

    /** DRAFT | SENDING | SENT | FAILED */
    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "sent_by", length = 200)
    private String sentBy;

    @Column(name = "sent_count", nullable = false)
    private Integer sentCount;

    @Column(name = "fail_count", nullable = false)
    private Integer failCount;
}
