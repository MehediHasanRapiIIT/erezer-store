package kn.org.deliverybackend.enumeration;

import kn.org.deliverybackend.exception.InvalidRequestException;

import java.util.Locale;

/**
 * The cut a garment is offered in. A product may come in one, both, or neither;
 * when it comes in any, each fit has its own sizes, stock and (optionally) price,
 * and the customer picks the fit before the size.
 */
public enum Fit {

    DROP_SHOULDER("Drop Shoulder", "DS"),
    REGULAR_FIT("Regular Fit", "RF");

    private final String label;
    private final String code;

    Fit(String label, String code) {
        this.label = label;
        this.code = code;
    }

    /** As customers and staff read it: "Drop Shoulder". */
    public String label() {
        return label;
    }

    /** Two letters for a SKU: "DS". */
    public String code() {
        return code;
    }

    /** The fit named by a stored or submitted value; null for none. An unknown name is refused. */
    public static Fit parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String name = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (Fit fit : values()) {
            if (fit.name().equals(name)) return fit;
        }
        throw new InvalidRequestException("Unknown fit: " + raw.trim() + ". Choose Drop Shoulder or Regular Fit.");
    }

    /** "Drop Shoulder" for a stored value; null for none or for a value no longer known. */
    public static String labelOf(String stored) {
        if (stored == null || stored.isBlank()) return null;
        try {
            return valueOf(stored).label;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** "Drop Shoulder / M" for a size in a fit, or just "M" when the product has no fits. */
    public static String describe(String storedFit, String size) {
        String fit = labelOf(storedFit);
        if (fit == null) return size;
        return size == null || size.isBlank() ? fit : fit + " / " + size;
    }

    /** Drop Shoulder before Regular Fit before no fit, for lists. */
    public static int rank(String stored) {
        if (stored == null || stored.isBlank()) return values().length;
        try {
            return valueOf(stored).ordinal();
        } catch (IllegalArgumentException e) {
            return values().length;
        }
    }
}
