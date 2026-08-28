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
    private String socialHandle;
    private String socialUrl;
    /** Lookbook gallery image URLs. */
    private List<String> images;
}
