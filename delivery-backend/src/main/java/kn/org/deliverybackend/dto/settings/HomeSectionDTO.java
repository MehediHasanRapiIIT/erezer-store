package kn.org.deliverybackend.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One section of the home page in the shop's chosen layout: which, and whether it is shown. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeSectionDTO {

    /** A {@code HomeSection} name, e.g. NEW_ARRIVALS. */
    private String key;

    private boolean enabled;
}
