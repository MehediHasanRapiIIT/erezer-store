package kn.org.deliverybackend.enumeration;

import java.util.Optional;

/**
 * Where a promotional banner is rendered on the storefront home page.
 *
 * <p>Banners used to be a single undifferentiated list that only fed the hero
 * carousel. The editorial landing page has several distinct picture slots, so a
 * banner now declares which one it belongs to and the storefront asks for the
 * slot it needs rather than guessing by position.
 *
 * <p>Every band self-hides when its slot has no active banner, so an incomplete
 * set degrades to the previous layout instead of leaving empty frames.
 */
public enum BannerSlot {

    /** Full-viewport opening band. Multiple HERO banners rotate as a carousel. */
    HERO,

    /** Left half of the two-up category band. */
    SPLIT_LEFT,

    /** Right half of the two-up category band. */
    SPLIT_RIGHT,

    /** The 2x2 tile grid, in reading order. */
    GRID_1,
    GRID_2,
    GRID_3,
    GRID_4,

    /** Photo beside the "design your own" promo panel. */
    CUSTOM_PROMO;

    /**
     * Lenient parse for request params. Returns empty rather than throwing so an
     * unknown value can fall back to the default instead of failing the upload.
     */
    public static Optional<BannerSlot> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(BannerSlot.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
