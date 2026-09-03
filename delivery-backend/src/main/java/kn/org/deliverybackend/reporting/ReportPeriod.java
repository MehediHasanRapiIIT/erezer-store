package kn.org.deliverybackend.reporting;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A half-open date range {@code [start, endExclusive)} in the business
 * calendar's local dates, plus the period type it was derived from.
 *
 * <p>Half-open on purpose: "to midnight of the next day" has no off-by-one
 * at the boundary, and it converts to a UTC instant range without needing a
 * "23:59:59.999" fudge.
 */
public record ReportPeriod(PeriodType type, LocalDate start, LocalDate endExclusive) {

    public ReportPeriod {
        if (type == null) throw new IllegalArgumentException("type is required");
        if (start == null || endExclusive == null) throw new IllegalArgumentException("start and end are required");
        if (!endExclusive.isAfter(start)) throw new IllegalArgumentException("endExclusive must be after start");
    }

    /** Last calendar date inside the period. */
    public LocalDate endInclusive() {
        return endExclusive.minusDays(1);
    }

    public long days() {
        return ChronoUnit.DAYS.between(start, endExclusive);
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && date.isBefore(endExclusive);
    }
}
