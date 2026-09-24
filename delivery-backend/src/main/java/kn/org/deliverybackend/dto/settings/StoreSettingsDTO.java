package kn.org.deliverybackend.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Store-wide settings surfaced on the storefront product page and edited from
 * the admin "Store Settings" screen.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreSettingsDTO {
    private String returnPolicyText;
    private Integer exchangeWindowDays;
    private String supportPhone;
    private String supportEmail;
    private String supportHours;
    private SizeChartDTO sizeChart;
    private BrandStoryDTO brandStory;
    private AboutPageDTO aboutPage;
    private FooterDTO footer;
    private MarqueeDTO marquee;
    /** Home-page "highlights" stat band. */
    private List<HighlightDTO> highlights;

    /** Checkout payment methods the admin has enabled. */
    private Boolean paymentCodEnabled;
    private Boolean paymentBkashEnabled;
    private Boolean paymentCardEnabled;

    /**
     * Automatic-discount switches. Null means enabled, so an older client that
     * does not send them cannot silently switch discounting off.
     */
    private Boolean discountsEnabled;
    private Boolean discountsGlobalEnabled;
    private Boolean discountsCategoryEnabled;
    private Boolean discountsProductEnabled;

    /** Promo codes on or off. Read-only here: it has its own endpoint and permission. */
    private Boolean couponsEnabled;

    /**
     * Meta Pixel ID for the shop's pages, or null when reporting is off. Public
     * by nature: the pixel puts it in the page source. The access token is not here.
     */
    private String metaPixelId;

    /**
     * Shipping rules, for the storefront's "free shipping" messages. Read-only
     * here: they are changed on the admin Shipping page.
     */
    private Boolean shippingFreeAll;
    private Boolean shippingOfferEnabled;
    private java.math.BigDecimal shippingOfferMin;
}
