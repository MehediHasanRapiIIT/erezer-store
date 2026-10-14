package kn.org.deliverybackend.enumeration;

import kn.org.deliverybackend.exception.InvalidRequestException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A cut a garment is offered in: Drop Shoulder, Regular Fit, and whatever the
 * shop adds later (Oversized). A product may come in any number of them; each
 * has its own sizes, stock, (optionally) price and size chart, and the customer
 * picks the fit before the size.
 *
 * <p>The list is the shop's, kept in the {@code fit} table and managed on the
 * Size Charts page. {@code FitCatalog} puts it here when the server starts and
 * after every change, so the rest of the program reads fits the same simple way
 * it did when there were only two. A variant names its fit by {@link #name()}
 * (DROP_SHOULDER), which never changes once a fit exists.
 */
public final class Fit {

    /** The two fits the shop has always had, so the program works before the list is read (and in tests). */
    public static final Fit DROP_SHOULDER = new Fit("DROP_SHOULDER", "Drop Shoulder", "DS", 0, null, false);
    public static final Fit REGULAR_FIT = new Fit("REGULAR_FIT", "Regular Fit", "RF", 1, null, false);

    private static volatile List<Fit> registry = List.of(DROP_SHOULDER, REGULAR_FIT);

    private final String name;
    private final String label;
    private final String code;
    private final int ordinal;
    private final Long sizeChartId;
    private final boolean hidden;

    public Fit(String name, String label, String code, int ordinal, Long sizeChartId, boolean hidden) {
        this.name = name;
        this.label = label;
        this.code = code;
        this.ordinal = ordinal;
        this.sizeChartId = sizeChartId;
        this.hidden = hidden;
    }

    /** Replaces the list, in the shop's order. Called by {@code FitCatalog}. */
    public static void install(List<Fit> fits) {
        List<Fit> ordered = new ArrayList<>();
        for (int i = 0; i < fits.size(); i++) {
            Fit f = fits.get(i);
            ordered.add(new Fit(f.name, f.label, f.code, i, f.sizeChartId, f.hidden));
        }
        registry = List.copyOf(ordered);
    }

    /** As stored on a variant: DROP_SHOULDER. Never changes once the fit exists. */
    public String name() {
        return name;
    }

    /** As customers and staff read it: "Drop Shoulder". */
    public String label() {
        return label;
    }

    /** Two or three letters for a SKU: "DS". */
    public String code() {
        return code;
    }

    /** Its place in the shop's list, from 0. */
    public int ordinal() {
        return ordinal;
    }

    /** The library chart this fit's sizes show; null to follow the product's chart. */
    public Long sizeChartId() {
        return sizeChartId;
    }

    /** Hidden: kept for products that already use it, not offered for new ones. */
    public boolean hidden() {
        return hidden;
    }

    /** Every fit, in the shop's order, hidden ones included. */
    public static Fit[] values() {
        return registry.toArray(new Fit[0]);
    }

    /** The fit stored as {@code stored}; refused when the shop has no such fit. */
    public static Fit valueOf(String stored) {
        for (Fit fit : registry) {
            if (fit.name.equals(stored)) return fit;
        }
        throw new IllegalArgumentException("No fit " + stored);
    }

    /** The fit named by a stored or submitted value ("REGULAR_FIT", "Regular Fit"); null for none. An unknown name is refused. */
    public static Fit parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String name = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (Fit fit : registry) {
            if (fit.name.equals(name) || fit.label.equalsIgnoreCase(raw.trim())) return fit;
        }
        List<String> offered = registry.stream().filter(f -> !f.hidden).map(f -> f.label).toList();
        throw new InvalidRequestException("Unknown fit: " + raw.trim() + ". Choose "
                + (offered.size() <= 1 ? String.join("", offered)
                : String.join(", ", offered.subList(0, offered.size() - 1)) + " or " + offered.get(offered.size() - 1)) + ".");
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

    /** The shop's order of fits, then no fit, for lists. */
    public static int rank(String stored) {
        if (stored == null || stored.isBlank()) return registry.size();
        try {
            return valueOf(stored).ordinal;
        } catch (IllegalArgumentException e) {
            return registry.size();
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Fit other && other.name.equals(name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
