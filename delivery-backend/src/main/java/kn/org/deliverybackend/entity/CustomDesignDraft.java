package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A saved, re-editable design belonging to a logged-in customer ("save" /
 * "saved draft"). {@code shareToken} backs the public "share design" read-only
 * preview link.
 */
@Entity
@Table(name = "custom_design_draft",
        indexes = {
                @Index(name = "idx_cd_draft_user", columnList = "user_id, updated_at DESC"),
                @Index(name = "idx_cd_draft_share", columnList = "share_token")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomDesignDraft extends AbstractBaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "item_name", length = 150)
    private String itemName;

    @Column(name = "color_name", length = 80)
    private String colorName;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    @Column(name = "design_json", columnDefinition = "text")
    private String designJson;

    /** Opaque token for the public share link; null until the customer shares. */
    @Column(name = "share_token", length = 64, unique = true)
    private String shareToken;
}
