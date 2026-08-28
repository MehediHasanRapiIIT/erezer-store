package kn.org.deliverybackend.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single "Our promise" item in the footer: a preset icon key plus a short
 * title and description (e.g. "Nationwide Delivery").
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FooterPromiseDTO {
    /** Preset icon key: quality | support | delivery | globe | star. */
    private String icon;
    private String title;
    private String description;
}
