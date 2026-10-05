package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.settings.BrandStoryDTO;
import kn.org.deliverybackend.dto.settings.SocialLinkDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The social accounts shown with "Our story".
 *
 * <p>The story used to hold one handle and one link ({@code socialHandle},
 * {@code socialUrl}); it now holds a list. Stories saved before the list
 * existed are read as a list of that one, so nothing a shop typed is lost, and
 * the two old fields are kept in step with the first of the list so a shop page
 * that still reads them goes on working.
 */
public final class BrandStorySocials {

    /** Plenty for a brand, and few enough to sit on one line under the story. */
    public static final int MAX = 8;
    private static final int MAX_HANDLE = 60;
    private static final int MAX_URL = 300;

    private BrandStorySocials() {}

    /** Tidies a story's social accounts in place and returns it; null stays null. */
    public static BrandStoryDTO tidy(BrandStoryDTO story) {
        if (story == null) return null;

        List<SocialLinkDTO> typed = story.getSocials();
        if (typed == null) {
            // Saved before there was a list: the one old handle is the list.
            typed = new ArrayList<>();
            if (hasText(story.getSocialHandle()) || hasText(story.getSocialUrl())) {
                typed.add(new SocialLinkDTO(story.getSocialHandle(), story.getSocialUrl()));
            }
        }

        List<SocialLinkDTO> clean = new ArrayList<>();
        for (SocialLinkDTO row : typed) {
            if (row == null || clean.size() >= MAX) continue;
            String url = cleanUrl(row.getUrl());
            String handle = clip(row.getHandle(), MAX_HANDLE);
            if (handle == null && url == null) continue;        // an empty row
            if (handle == null) handle = readable(url);         // a link with no name: show the link itself
            clean.add(new SocialLinkDTO(handle, url));
        }

        story.setSocials(clean);
        story.setSocialHandle(clean.isEmpty() ? null : clean.get(0).getHandle());
        story.setSocialUrl(clean.isEmpty() ? null : clean.get(0).getUrl());
        return story;
    }

    /**
     * A link customers can follow, or null. "instagram.com/erezer" is given its
     * https://; anything that is not a web address — a script, a file — is dropped.
     */
    static String cleanUrl(String raw) {
        String url = clip(raw, MAX_URL);
        if (url == null || url.chars().anyMatch(Character::isWhitespace)) return null;
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) return url;
        if (lower.matches("^[a-z][a-z0-9+.-]*:.*")) return null;    // some other scheme
        return url.contains(".") ? "https://" + url : null;
    }

    /** "https://www.instagram.com/erezer/" → "instagram.com/erezer". */
    private static String readable(String url) {
        String text = url.replaceFirst("(?i)^https?://(www\\.)?", "").replaceFirst("/+$", "");
        return clip(text, MAX_HANDLE);
    }

    private static String clip(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
