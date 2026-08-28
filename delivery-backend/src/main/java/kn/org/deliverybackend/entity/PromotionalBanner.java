package kn.org.deliverybackend.entity;

import kn.org.deliverybackend.enumeration.BannerSlot;
import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "promotional_banner")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PromotionalBanner extends AbstractBaseEntity<UUID> {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    private String imageUrl;

    private LocalDate fromDate;

    private LocalDate toDate;

    private String promotionTitle;

    private String promotionDetails;

    /**
     * Which home-page band this banner fills. Nullable for rows created before
     * the editorial landing page existed; those are treated as HERO, which is
     * the only place banners appeared previously.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "slot", length = 32)
    private BannerSlot slot;

    /** Button text, e.g. "SHOP NOW". Null hides the button. */
    @Column(name = "cta_label", length = 60)
    private String ctaLabel;

    /** Where the button goes, e.g. "/shop?category=2". Null hides the button. */
    @Column(name = "cta_link", length = 500)
    private String ctaLink;

    /** Ordering within a slot; lowest first. Matters for the HERO carousel. */
    @Column(name = "sort_order")
    private Integer sortOrder;
}
