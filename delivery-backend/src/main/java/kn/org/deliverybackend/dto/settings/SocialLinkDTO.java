package kn.org.deliverybackend.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One of the shop's social accounts: the name to show, and where it leads. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialLinkDTO {

    /** What customers read, e.g. "@erezer". */
    private String handle;

    /** Where it goes, e.g. https://instagram.com/erezer. May be empty: the handle is then shown as plain text. */
    private String url;
}
