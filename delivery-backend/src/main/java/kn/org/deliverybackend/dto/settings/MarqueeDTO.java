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
    /**
     * No longer read. Whether the strip is shown is the home page layout's
     * MARQUEE switch (V22); V23 carried every shop's old "off" over to it.
     * Kept so settings saved by an older admin panel still parse.
     */
    private Boolean enabled;
    private List<String> items;
}
