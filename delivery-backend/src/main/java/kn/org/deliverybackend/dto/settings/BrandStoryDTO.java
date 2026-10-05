package kn.org.deliverybackend.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** The editorial "Our story" band on the storefront landing page. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandStoryDTO {
    private String eyebrow;
    private String heading;
    private String body;
    private String ctaLabel;
    private String ctaLink;
    /**
     * The shop's social accounts, in the order to show them. Null only on a
     * story saved before the list existed; see {@code BrandStorySocials}.
     */
    private List<SocialLinkDTO> socials;

    /**
     * The first of {@link #socials}, kept for a shop page that still reads a
     * single handle. Set by the server; use {@link #socials} instead.
     */
    private String socialHandle;
    private String socialUrl;
    /** Lookbook gallery image URLs. */
    private List<String> images;
}
