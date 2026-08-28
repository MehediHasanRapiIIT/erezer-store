package kn.org.deliverybackend.entity;

import jakarta.persistence.*;
import kn.org.deliverybackend.entity.base.AbstractBaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single-row store-wide settings, surfaced on the storefront product page:
 * the return/exchange policy blurb, customer-support contact details, and the
 * size chart. The row is a singleton (id == {@link #SINGLETON_ID}); the service
 * lazily seeds defaults the first time it is read.
 */
@Entity
@Table(name = "store_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreSettings extends AbstractBaseEntity<Long> {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    /** Free-text return/exchange policy shown on every product page. */
    @Column(name = "return_policy_text", columnDefinition = "text")
    private String returnPolicyText;

    /** Days within which a customer must notify us for a return/exchange (e.g. 3). */
    @Column(name = "exchange_window_days")
    private Integer exchangeWindowDays;

    @Column(name = "support_phone", length = 32)
    private String supportPhone;

    @Column(name = "support_email", length = 128)
    private String supportEmail;

    @Column(name = "support_hours", length = 120)
    private String supportHours;

    /**
     * Size chart serialised as JSON. Shape:
     * {@code {"columns":["Chest","Length"],"rows":[{"size":"S","cells":[{"cm":96,"inch":37.8},{"cm":68,"inch":26.8}]}]}}
     * Stored opaquely; the admin UI authors it and the storefront renders it.
     */
    @Column(name = "size_chart_json", columnDefinition = "text")
    private String sizeChartJson;

    /** {@code BrandStoryDTO} serialised as JSON (landing "Our story" band). */
    @Column(name = "brand_story_json", columnDefinition = "text")
    private String brandStoryJson;

    /** {@code FooterDTO} serialised as JSON (storefront footer). */
    @Column(name = "footer_json", columnDefinition = "text")
    private String footerJson;

    /** {@code MarqueeDTO} serialised as JSON (landing trust strip). */
    @Column(name = "marquee_json", columnDefinition = "text")
    private String marqueeJson;

    /** {@code List<HighlightDTO>} serialised as JSON (home highlights band). */
    @Column(name = "highlights_json", columnDefinition = "text")
    private String highlightsJson;

    // ── Checkout payment methods (admin-toggled). Null on legacy rows → treated
    //    as enabled so existing behaviour is preserved until an admin edits them.
    @Column(name = "payment_cod_enabled")
    private Boolean paymentCodEnabled;

    @Column(name = "payment_bkash_enabled")
    private Boolean paymentBkashEnabled;

    @Column(name = "payment_card_enabled")
    private Boolean paymentCardEnabled;
}
