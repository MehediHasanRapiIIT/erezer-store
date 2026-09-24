package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.settings.MetaPixelChangeDTO;
import kn.org.deliverybackend.dto.settings.MetaPixelSettingsDTO;

/**
 * The shop's Meta (Facebook) Pixel settings, kept in the database so the owner
 * can change them in the admin panel instead of on the server.
 */
public interface MetaPixelSettingsService {

    /** What the admin panel shows. Never includes the access token itself. */
    MetaPixelSettingsDTO get();

    /** Saves what the owner typed and returns the settings as they now stand. */
    MetaPixelSettingsDTO update(MetaPixelChangeDTO change);

    /** What the shop should use right now: the saved settings, else the server file. */
    MetaCredentials credentials();

    /** Asks Meta to accept one test event, so the owner can see it working. */
    String sendTestEvent();

    /**
     * The settings in use.
     *
     * @param pixelId       the Pixel ID, or null when reporting is off
     * @param accessToken   the Conversions API token, or null for browser-only reporting
     * @param testEventCode Meta's test code, or null in normal use
     */
    record MetaCredentials(String pixelId, String accessToken, String testEventCode) {

        /** True when the shop's pages should load the pixel. */
        public boolean browserPixelOn() {
            return pixelId != null && !pixelId.isBlank();
        }

        /** True when the server should also report sales (survives ad blockers). */
        public boolean serverReportingOn() {
            return browserPixelOn() && accessToken != null && !accessToken.isBlank();
        }
    }
}
