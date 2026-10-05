package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.settings.BrandStoryDTO;
import kn.org.deliverybackend.dto.settings.SocialLinkDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** "Our story" can carry several social accounts, where it used to carry one. */
class BrandStorySocialsTest {

    private static SocialLinkDTO social(String handle, String url) {
        return new SocialLinkDTO(handle, url);
    }

    private static BrandStoryDTO story(SocialLinkDTO... socials) {
        BrandStoryDTO s = new BrandStoryDTO();
        s.setSocials(new ArrayList<>(Arrays.asList(socials)));
        return s;
    }

    @Test
    void severalAccountsAreKeptInTheOrderGiven() {
        BrandStoryDTO s = BrandStorySocials.tidy(story(
                social("@erezer", "https://instagram.com/erezer"),
                social("Erezer", "https://facebook.com/erezer"),
                social("@erezer.bd", "https://tiktok.com/@erezer.bd")));
        assertEquals(List.of("@erezer", "Erezer", "@erezer.bd"),
                s.getSocials().stream().map(SocialLinkDTO::getHandle).toList());
    }

    @Test
    void aStorySavedWithOneHandleIsReadAsAListOfThatOne() {
        BrandStoryDTO old = new BrandStoryDTO();          // no list at all: saved before it existed
        old.setSocialHandle("@erezer");
        old.setSocialUrl("https://instagram.com/erezer");

        BrandStoryDTO s = BrandStorySocials.tidy(old);

        assertEquals(1, s.getSocials().size(), "the shop's one handle is not lost");
        assertEquals("@erezer", s.getSocials().get(0).getHandle());
        assertEquals("https://instagram.com/erezer", s.getSocials().get(0).getUrl());
    }

    @Test
    void removingEveryAccountLeavesNoneRatherThanBringingTheOldOneBack() {
        BrandStoryDTO s = story();                         // an empty list, on purpose
        s.setSocialHandle("@erezer");
        s.setSocialUrl("https://instagram.com/erezer");

        BrandStorySocials.tidy(s);

        assertTrue(s.getSocials().isEmpty());
        assertNull(s.getSocialHandle(), "the old single fields follow the list");
        assertNull(s.getSocialUrl());
    }

    @Test
    void theOldSingleFieldsFollowTheFirstOfTheList() {
        BrandStoryDTO s = BrandStorySocials.tidy(story(
                social("Erezer", "https://facebook.com/erezer"), social("@erezer", "https://instagram.com/erezer")));
        assertEquals("Erezer", s.getSocialHandle());
        assertEquals("https://facebook.com/erezer", s.getSocialUrl());
    }

    @Test
    void emptyRowsAreDroppedAndSpacesTrimmed() {
        BrandStoryDTO s = BrandStorySocials.tidy(story(
                social("  @erezer  ", "  https://instagram.com/erezer "), social("", ""), social("   ", null), null));
        assertEquals(1, s.getSocials().size());
        assertEquals("@erezer", s.getSocials().get(0).getHandle());
        assertEquals("https://instagram.com/erezer", s.getSocials().get(0).getUrl());
    }

    @Test
    void aLinkTypedWithoutHttpsIsGivenIt() {
        assertEquals("https://instagram.com/erezer", BrandStorySocials.cleanUrl("instagram.com/erezer"));
        assertEquals("https://www.facebook.com/erezer", BrandStorySocials.cleanUrl("www.facebook.com/erezer"));
        assertEquals("http://example.com", BrandStorySocials.cleanUrl("http://example.com"));
    }

    @Test
    void anythingThatIsNotAWebAddressIsNotKeptAsALink() {
        assertNull(BrandStorySocials.cleanUrl("javascript:alert(1)"));
        assertNull(BrandStorySocials.cleanUrl("data:text/html,hi"));
        assertNull(BrandStorySocials.cleanUrl("just some words"));
        assertNull(BrandStorySocials.cleanUrl("erezer"));
        assertNull(BrandStorySocials.cleanUrl("  "));
    }

    @Test
    void aHandleWithNoLinkIsKeptAsPlainText() {
        BrandStoryDTO s = BrandStorySocials.tidy(story(social("@erezer", "")));
        assertEquals("@erezer", s.getSocials().get(0).getHandle());
        assertNull(s.getSocials().get(0).getUrl());
    }

    @Test
    void aLinkWithNoNameShowsTheLinkItself() {
        BrandStoryDTO s = BrandStorySocials.tidy(story(social("", "https://www.instagram.com/erezer/")));
        assertEquals("instagram.com/erezer", s.getSocials().get(0).getHandle());
    }

    @Test
    void thereIsALimit() {
        SocialLinkDTO[] many = new SocialLinkDTO[12];
        for (int i = 0; i < many.length; i++) many[i] = social("@a" + i, "https://example.com/" + i);
        assertEquals(BrandStorySocials.MAX, BrandStorySocials.tidy(story(many)).getSocials().size());
    }

    @Test
    void noStoryAtAllIsLeftAlone() {
        assertNull(BrandStorySocials.tidy(null));
    }
}
