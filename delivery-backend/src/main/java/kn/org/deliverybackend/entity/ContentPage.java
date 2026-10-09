package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * A page of the shop's own words - "Our Mission", "Our Values" - written in the
 * admin panel and read at {@code /pages/<slug>}: a title, an opening, a list of
 * sections and a closing line.
 */
@Entity
@Table(name = "content_page")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class ContentPage extends AbstractBaseEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The last part of the page's address: "our-mission". Unique among live pages. */
    @Column(nullable = false, length = 140)
    private String slug;

    @Column(nullable = false, length = 160)
    private String title;

    /** A small line above the title, e.g. "EREZER". */
    @Column(length = 80)
    private String eyebrow;

    /** The opening. Its first paragraph is set large; the rest follow as ordinary text. */
    @Column(columnDefinition = "text")
    private String intro;

    /** An optional picture behind the title. */
    @Column(name = "hero_image_url", length = 1000)
    private String heroImageUrl;

    /** The sections as JSON: [{"heading":"…","body":"…","imageUrl":null}, …]. */
    @Column(name = "sections_json", columnDefinition = "text")
    private String sectionsJson;

    /** The line the page ends on, set large. */
    @Column(columnDefinition = "text")
    private String closing;

    /** A button under the closing line; both null for none. */
    @Column(name = "cta_label", length = 80)
    private String ctaLabel;

    @Column(name = "cta_link", length = 300)
    private String ctaLink;

    /** Linked from the footer's first column. */
    @Column(name = "show_in_footer", nullable = false)
    private Boolean showInFooter = Boolean.TRUE;

    /** Switched off, the page can't be opened and is not linked anywhere. */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
