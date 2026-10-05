package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.settings.HomeSectionDTO;
import kn.org.deliverybackend.enumeration.HomeSection;
import kn.org.deliverybackend.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The home page's layout: an admin chooses which sections are shown and in what
 * order. A layout always names every section exactly once.
 */
class HomeLayoutTest {

    private static HomeSectionDTO row(String key, boolean enabled) {
        return new HomeSectionDTO(key, enabled);
    }

    private static List<String> keys(List<HomeSectionDTO> layout) {
        return layout.stream().map(HomeSectionDTO::getKey).toList();
    }

    private static final List<String> EVERY_SECTION =
            Arrays.stream(HomeSection.values()).map(Enum::name).toList();

    @Test
    void aShopThatNeverArrangedItsPageGetsItAsItWasBuilt() {
        for (List<HomeSectionDTO> nothing : Arrays.<List<HomeSectionDTO>>asList(null, List.of())) {
            List<HomeSectionDTO> layout = HomeLayout.complete(nothing);
            assertEquals(EVERY_SECTION, keys(layout), "every section, in the original order");
            assertTrue(layout.stream().allMatch(HomeSectionDTO::isEnabled), "all switched on");
        }
    }

    @Test
    void theOriginalOrderIsTheOneThePageHasAlwaysHad() {
        assertEquals(List.of("SPLIT_BAND", "TILE_GRID", "CUSTOM_DESIGN_PROMO", "FLASH_SALE", "FEATURED_BUNDLE",
                "SHOP_BY_CATEGORY", "HIGHLIGHTS", "NEW_ARRIVALS", "FEATURED_PRODUCTS", "CATEGORY_COLLECTIONS",
                "MARQUEE", "RECENTLY_VIEWED", "OUR_STORY", "NEWSLETTER"), keys(HomeLayout.original()));
    }

    @Test
    void theShopsOrderAndSwitchesAreKept() {
        List<HomeSectionDTO> layout = HomeLayout.complete(List.of(
                row("OUR_STORY", true), row("NEW_ARRIVALS", false), row("FEATURED_PRODUCTS", true)));
        assertEquals(List.of("OUR_STORY", "NEW_ARRIVALS", "FEATURED_PRODUCTS"), keys(layout).subList(0, 3));
        assertFalse(layout.get(1).isEnabled(), "New arrivals stays switched off");
    }

    @Test
    void aSectionAddedToTheShopLaterGoesAtTheEndSwitchedOn() {
        // A layout saved before "Recently viewed" and the rest existed.
        List<HomeSectionDTO> layout = HomeLayout.complete(List.of(row("OUR_STORY", false), row("HIGHLIGHTS", true)));
        assertEquals(EVERY_SECTION.size(), layout.size(), "every section is named");
        assertEquals("OUR_STORY", layout.get(0).getKey());
        assertTrue(layout.subList(2, layout.size()).stream().allMatch(HomeSectionDTO::isEnabled));
    }

    @Test
    void whatWasStoredIsTidiedRatherThanBreakingTheHomePage() {
        // A section that no longer exists, one listed twice, and a missing key.
        List<HomeSectionDTO> layout = HomeLayout.complete(Arrays.asList(
                row("A_SECTION_FROM_THE_PAST", true), row("MARQUEE", false), row("marquee", true), row(null, true), null));
        assertEquals(EVERY_SECTION.size(), layout.size());
        assertEquals("MARQUEE", layout.get(0).getKey());
        assertFalse(layout.get(0).isEnabled(), "the first mention wins");
    }

    @Test
    void savingChecksWhatTheAdminSends() {
        InvalidRequestException unknown = assertThrows(InvalidRequestException.class,
                () -> HomeLayout.checked(List.of(row("HERO", true))));
        assertTrue(unknown.getMessage().contains("HERO"), "the top banner is not something to arrange");

        assertThrows(InvalidRequestException.class,
                () -> HomeLayout.checked(List.of(row("MARQUEE", true), row("MARQUEE", false))));
        assertThrows(InvalidRequestException.class, () -> HomeLayout.checked(null));
    }

    @Test
    void aSavedLayoutIsCompleteAndInTheOrderSent() {
        List<HomeSectionDTO> saved = HomeLayout.checked(List.of(row("OUR_STORY", true), row("FLASH_SALE", false)));
        assertEquals(EVERY_SECTION.size(), saved.size());
        assertEquals(List.of("OUR_STORY", "FLASH_SALE"), keys(saved).subList(0, 2));
    }
}
