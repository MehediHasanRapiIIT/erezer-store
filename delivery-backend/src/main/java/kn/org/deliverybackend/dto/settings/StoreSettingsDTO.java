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
    private FooterDTO footer;
    private MarqueeDTO marquee;
    /** Home-page "highlights" stat band. */
    private List<HighlightDTO> highlights;

    /** Checkout payment methods the admin has enabled. */
    private Boolean paymentCodEnabled;
    private Boolean paymentBkashEnabled;
    private Boolean paymentCardEnabled;
}
