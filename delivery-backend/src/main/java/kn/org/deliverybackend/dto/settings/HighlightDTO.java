package kn.org.deliverybackend.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single "highlights" stat shown on the home page below the category tiles
 * (e.g. value "4.9 / 5", label "Customer rating").
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HighlightDTO {
    /** Preset icon key: star | truck | refresh | shield. */
    private String icon;
    /** The displayed stat, e.g. "4.9 / 5", "2–4 days", "256-bit SSL". */
    private String value;
    private String label;
    private String description;
}
