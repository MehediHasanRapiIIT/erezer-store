package kn.org.deliverybackend.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The rules for giving a category its product codes (PRODUCT-CODE-PLAN.md,
 * part 2): either the same code for every product, or a prefix with running
 * numbers. Kept apart from the database so the rules can be tested on their own.
 */
public final class CategoryCodeChange {

    /** What every ticked product in the category gets. */
    public enum CodeMode {
        /** The same code, typed once: "EP-1001" for all of them. Codes may be shared. */
        SAME,
        /** A prefix with running numbers in name order: "EP-001", "EP-002"… */
        NUMBERED
    }

    /** The longest a product code may be, as everywhere else. */
    public static final int MAX_CODE_LENGTH = 40;

    private static final Pattern PREFIX = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,19}");

    private CategoryCodeChange() {}

    /**
     * Why the typed value can't be used, in words for the page; null when it can.
     * The value is the code itself for {@link CodeMode#SAME}, and the prefix for
     * {@link CodeMode#NUMBERED}.
     */
    public static String invalidReason(CodeMode mode, String value) {
        if (mode == null) {
            return "Choose whether every product gets the same code or numbered ones.";
        }
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            return mode == CodeMode.SAME ? "Type the code, for example EP-1001." : "Type a prefix, for example EP.";
        }
        if (mode == CodeMode.SAME) {
            return v.length() > MAX_CODE_LENGTH
                    ? "The code must be " + MAX_CODE_LENGTH + " characters or fewer." : null;
        }
        if (v.length() > 20) {
            return "The prefix must be 20 characters or fewer.";
        }
        if (!PREFIX.matcher(v).matches()) {
            return "The prefix can use letters, numbers, dashes and underscores, and must start with a letter or number.";
        }
        return null;
    }

    /** "EP-001". Numbers past 999 simply grow: "EP-1000". */
    public static String code(String prefix, int number) {
        String p = prefix.trim();
        return number < 1000 ? String.format("%s-%03d", p, number) : p + "-" + number;
    }

    /** The number in a code with this prefix ("EP-004" → 4); 0 when the code doesn't use it. */
    public static int numberOf(String prefix, String code) {
        if (code == null) {
            return 0;
        }
        Matcher m = Pattern.compile("^" + Pattern.quote(prefix.trim()) + "-0*(\\d{1,9})$",
                Pattern.CASE_INSENSITIVE).matcher(code.trim());
        return m.matches() ? Integer.parseInt(m.group(1)) : 0;
    }
}
