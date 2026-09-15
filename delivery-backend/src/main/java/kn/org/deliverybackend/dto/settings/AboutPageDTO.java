package kn.org.deliverybackend.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** The storefront About Us page: a title and intro over a main photo, text sections, and a closing button. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AboutPageDTO {
    private String title;
    private String intro;
    private String heroImageUrl;
    private List<AboutSectionDTO> sections;
    private String ctaLabel;
    private String ctaLink;
}
