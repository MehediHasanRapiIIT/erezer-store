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
 * A colourway of a {@link CustomDesignItem}. Holds the four mockup images
 * (one per view) that the studio uses as the canvas background so the customer's
 * artwork appears composited onto the garment.
 */
@Entity
@Table(name = "custom_design_color",
        indexes = {
                @Index(name = "idx_cd_color_item", columnList = "item_id, sort_order")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomDesignColor extends AbstractBaseEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private CustomDesignItem item;

    @Column(nullable = false, length = 80)
    private String name;

    /** Hex swatch, e.g. #FFFFFF. */
    @Column(nullable = false, length = 9)
    private String hex;

    @Column(name = "front_image_url", length = 1000)
    private String frontImageUrl;

    @Column(name = "back_image_url", length = 1000)
    private String backImageUrl;

    @Column(name = "left_sleeve_image_url", length = 1000)
    private String leftSleeveImageUrl;

    @Column(name = "right_sleeve_image_url", length = 1000)
    private String rightSleeveImageUrl;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;
}
