package kn.org.deliverybackend.enumeration;

import java.util.Optional;

/**
 * The sections of the shop's home page an admin can switch on or off and
 * arrange. Declared in the order the page was originally built, which is the
 * order a shop sees until it changes it.
 *
 * <p>The big top banner is deliberately not here: the page is designed around
 * it, so it is always first and always shown.
 *
 * <p>Adding a section: add it here, and to the storefront's home page and the
 * admin's "Home page layout" list. A shop that already saved a layout gets the
 * new section at the end, switched on.
 */
public enum HomeSection {
    /** Two large category panels side by side. */
    SPLIT_BAND,
    /** The 2x2 grid of category tiles. */
    TILE_GRID,
    /** The custom-design promotion band. */
    CUSTOM_DESIGN_PROMO,
    /** The running flash sale, if there is one. */
    FLASH_SALE,
    /** The featured bundle offer, if there is one. */
    FEATURED_BUNDLE,
    /** "Shop by category": the row of category pictures. */
    SHOP_BY_CATEGORY,
    /** The row of trust icons (delivery, quality, support...). */
    HIGHLIGHTS,
    /** Products marked as new arrivals. */
    NEW_ARRIVALS,
    /** Products marked as featured. */
    FEATURED_PRODUCTS,
    /** One band per category marked "show on home"; they move as one block. */
    CATEGORY_COLLECTIONS,
    /** The scrolling strip of short phrases. */
    MARQUEE,
    /** What this customer looked at recently. */
    RECENTLY_VIEWED,
    /** "Our story" and its gallery. */
    OUR_STORY,
    /** The newsletter sign-up at the foot of the page. */
    NEWSLETTER;

    public static Optional<HomeSection> parse(String raw) {
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(HomeSection.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
