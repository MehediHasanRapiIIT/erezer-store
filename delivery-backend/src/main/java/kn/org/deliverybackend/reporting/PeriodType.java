package kn.org.deliverybackend.reporting;

import java.util.Optional;

/**
 * The calendar periods a business report can cover.
 *
 * <p>Each type knows the unit its breakdown chart is drawn in: a day is
 * split by hour, a week or month by day, a year by month. That keeps every
 * report to a readable number of bars (24, 7, 28-31 or 12).
 */
public enum PeriodType {
    DAY("hour"),
    WEEK("day"),
    MONTH("day"),
    YEAR("month"),
    /** Bangladesh fiscal year: 1 July to 30 June. */
    FISCAL_YEAR("month");

    private final String bucketUnit;

    PeriodType(String bucketUnit) {
        this.bucketUnit = bucketUnit;
    }

    /** PostgreSQL {@code date_trunc} unit used for this period's breakdown. */
    public String bucketUnit() {
        return bucketUnit;
    }

    /** Case-insensitive parse; empty for unknown input so callers can 400. */
    public static Optional<PeriodType> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(PeriodType.valueOf(raw.trim().toUpperCase().replace('-', '_')));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
