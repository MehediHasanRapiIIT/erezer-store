package kn.org.deliverybackend.util;

import java.util.Locale;

/**
 * Typed search text turned into a LIKE pattern for the admin list queries.
 * The text is lower-cased (queries compare against LOWER(column)) and its own
 * %, _ and \ are escaped, so they are matched literally with ESCAPE '\'.
 */
public final class SearchText {

    private SearchText() {}

    /** "%text%", or null when there is nothing to search for (the query then skips the search). */
    public static String likePattern(String text) {
        if (text == null || text.isBlank()) return null;
        String lower = text.trim().toLowerCase(Locale.ROOT);
        return "%" + lower.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /** Page sizes a list will serve: at least 1, at most 100. */
    public static int pageSize(int requested) {
        return Math.min(Math.max(requested, 1), 100);
    }
}
