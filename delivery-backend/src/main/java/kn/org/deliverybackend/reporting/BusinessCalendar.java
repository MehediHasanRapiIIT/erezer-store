package kn.org.deliverybackend.reporting;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * The calendar the business actually runs on, as opposed to the one the
 * database or JVM happens to use.
 *
 * <p>Erezer trades in Bangladesh, so by default:
 * <ul>
 *   <li>"Today" is Asia/Dhaka (UTC+6, no daylight saving). An order placed at
 *       23:30 Dhaka time belongs to that day even though it is 17:30 UTC.</li>
 *   <li>The week starts on <b>Sunday</b>. The Bangladeshi weekend is Friday
 *       and Saturday, so Sunday-to-Saturday is the working week every shop
 *       owner, bank and courier reconciles against. ISO (Monday) weeks would
 *       split the weekend across two reports.</li>
 *   <li>The fiscal year runs <b>1 July – 30 June</b>, matching the National
 *       Board of Revenue's income-tax and VAT year.</li>
 * </ul>
 * All three are configurable under {@code app.business.*} so the same code
 * serves a shop in another market.
 *
 * <p>Timestamps are stored in the database as UTC wall-clock values
 * ({@code timestamp without time zone}); {@link #toUtc(LocalDate)} converts
 * a local calendar boundary into that representation for SQL windows.
 */
@Component
public class BusinessCalendar {

    private static final DateTimeFormatter DAY_LABEL   = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DAY   = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DAY_Y = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SQL_TS      = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ZoneId zone;
    private final DayOfWeek weekStart;
    private final Month fiscalYearStart;

    @Autowired
    public BusinessCalendar(@Value("${app.business.zone:Asia/Dhaka}") String zone,
                            @Value("${app.business.week-start:SUNDAY}") String weekStart,
                            @Value("${app.business.fiscal-year-start-month:7}") int fiscalYearStartMonth) {
        this(ZoneId.of(zone.trim()),
             DayOfWeek.valueOf(weekStart.trim().toUpperCase(Locale.ROOT)),
             fiscalYearStartMonth);
    }

    public BusinessCalendar(ZoneId zone, DayOfWeek weekStart, int fiscalYearStartMonth) {
        if (zone == null) throw new IllegalArgumentException("zone is required");
        if (weekStart == null) throw new IllegalArgumentException("weekStart is required");
        this.zone = zone;
        this.weekStart = weekStart;
        this.fiscalYearStart = Month.of(fiscalYearStartMonth);
    }

    public ZoneId zone() {
        return zone;
    }

    public DayOfWeek weekStart() {
        return weekStart;
    }

    public Month fiscalYearStart() {
        return fiscalYearStart;
    }

    /** The current business date, not the server's. */
    public LocalDate today() {
        return LocalDate.now(zone);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(zone);
    }

    // ── period arithmetic ───────────────────────────────────────────────────

    /** The period of the given type that contains {@code anchor}. */
    public ReportPeriod period(PeriodType type, LocalDate anchor) {
        if (type == null) throw new IllegalArgumentException("type is required");
        LocalDate day = anchor == null ? today() : anchor;
        return switch (type) {
            case DAY -> new ReportPeriod(type, day, day.plusDays(1));
            case WEEK -> {
                LocalDate start = day.with(TemporalAdjusters.previousOrSame(weekStart));
                yield new ReportPeriod(type, start, start.plusWeeks(1));
            }
            case MONTH -> {
                LocalDate start = day.withDayOfMonth(1);
                yield new ReportPeriod(type, start, start.plusMonths(1));
            }
            case YEAR -> {
                LocalDate start = day.withDayOfYear(1);
                yield new ReportPeriod(type, start, start.plusYears(1));
            }
            case FISCAL_YEAR -> {
                LocalDate start = fiscalYearStartFor(day);
                yield new ReportPeriod(type, start, start.plusYears(1));
            }
        };
    }

    /**
     * The period immediately before, of the same type. Used for
     * "compared to last week/month/year" deltas.
     */
    public ReportPeriod previous(ReportPeriod period) {
        return period(period.type(), period.start().minusDays(1));
    }

    /**
     * The same period one year earlier (this Eid week vs last year's). Only
     * meaningful for sub-year periods; for YEAR/FISCAL_YEAR it equals
     * {@link #previous}.
     */
    public ReportPeriod sameLastYear(ReportPeriod period) {
        return switch (period.type()) {
            case DAY -> period(PeriodType.DAY, period.start().minusYears(1));
            case WEEK -> period(PeriodType.WEEK, period.start().minusWeeks(52));
            default -> period(period.type(), period.start().minusYears(1));
        };
    }

    /** First day of the fiscal year containing {@code day}. */
    public LocalDate fiscalYearStartFor(LocalDate day) {
        LocalDate thisYearsStart = LocalDate.of(day.getYear(), fiscalYearStart, 1);
        return day.isBefore(thisYearsStart) ? thisYearsStart.minusYears(1) : thisYearsStart;
    }

    /**
     * Days to add before {@code date_trunc('week', …)} so that PostgreSQL's
     * Monday-based truncation lands on the configured week start. Subtract
     * the same number afterwards.
     *
     * <p>Sunday start → 1 (Sunday + 1 = Monday). Saturday start → 2. Monday → 0.
     */
    public int weekShiftDays() {
        return Math.floorMod(DayOfWeek.MONDAY.getValue() - weekStart.getValue(), 7);
    }

    // ── time-zone conversion ────────────────────────────────────────────────

    /** Midnight at the start of {@code localDate} in the business zone, as UTC wall-clock. */
    public LocalDateTime toUtc(LocalDate localDate) {
        return localDate.atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** {@link #toUtc(LocalDate)} formatted for a {@code CAST(:x AS timestamp)} SQL parameter. */
    public String toUtcSql(LocalDate localDate) {
        return SQL_TS.format(toUtc(localDate));
    }

    /** A UTC wall-clock database value shown in business-local time. */
    public LocalDateTime fromUtc(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime();
    }

    public ZonedDateTime atZone(LocalDateTime local) {
        return local.atZone(zone);
    }

    // ── labels ──────────────────────────────────────────────────────────────

    /** Human label, e.g. "Thu, 4 Sep 2026", "31 Aug – 6 Sep 2026", "FY 2026-27". */
    public String label(ReportPeriod period) {
        LocalDate s = period.start();
        LocalDate e = period.endInclusive();
        return switch (period.type()) {
            case DAY -> DAY_LABEL.format(s);
            case WEEK -> s.getYear() == e.getYear()
                    ? SHORT_DAY.format(s) + " – " + SHORT_DAY_Y.format(e)
                    : SHORT_DAY_Y.format(s) + " – " + SHORT_DAY_Y.format(e);
            case MONTH -> MONTH_LABEL.format(s);
            case YEAR -> String.valueOf(s.getYear());
            case FISCAL_YEAR -> fiscalYearStart == Month.JANUARY
                    ? String.valueOf(s.getYear())
                    : "FY " + s.getYear() + "-" + String.format("%02d", e.getYear() % 100);
        };
    }
}
