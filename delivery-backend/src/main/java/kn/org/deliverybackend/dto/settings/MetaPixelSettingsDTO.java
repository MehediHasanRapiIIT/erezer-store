package kn.org.deliverybackend.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** What the admin panel shows for Meta Pixel. The access token itself never leaves the server. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaPixelSettingsDTO {

    /** Whether the shop reports anything to Meta at all. */
    private boolean enabled;

    /** The Pixel (dataset) ID. Not a secret: it is visible in the shop's page source. */
    private String pixelId;

    /** True when a Conversions API access token is saved. */
    private boolean tokenSaved;

    /** The token's last few characters, e.g. "…a91F", so the owner can tell which one is saved. */
    private String tokenHint;

    /** Test event code from Meta's "Test events" tab; blank in normal use. */
    private String testEventCode;

    /** Where the settings in use come from: "admin", "server file" or "not set". */
    private String source;

    /** True when sales are also reported from the server (pixel id + token both present). */
    private boolean serverReporting;
}
