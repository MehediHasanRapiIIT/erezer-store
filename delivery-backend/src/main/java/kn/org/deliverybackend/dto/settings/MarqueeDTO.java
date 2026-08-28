package kn.org.deliverybackend.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** The scrolling trust strip on the storefront landing page. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarqueeDTO {
    /** When false the storefront hides the strip entirely. */
    private Boolean enabled;
    private List<String> items;
}
