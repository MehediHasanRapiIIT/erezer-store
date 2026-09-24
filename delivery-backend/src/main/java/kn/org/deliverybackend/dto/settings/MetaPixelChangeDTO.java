package kn.org.deliverybackend.dto.settings;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The Meta Pixel settings as saved from the admin panel. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetaPixelChangeDTO {

    private Boolean enabled;

    /** Meta's Pixel ID: digits only. Empty clears it. */
    @Size(max = 32, message = "That Pixel ID is too long")
    @Pattern(regexp = "^[0-9]*$", message = "A Pixel ID is numbers only, e.g. 1234567890123456")
    private String pixelId;

    /** A new access token. Left out (null), the saved one is kept. */
    @Size(max = 512, message = "That access token is too long")
    private String accessToken;

    /** True removes the saved access token; server-side reporting then stops. */
    private Boolean removeToken;

    @Size(max = 64, message = "That test event code is too long")
    private String testEventCode;
}
