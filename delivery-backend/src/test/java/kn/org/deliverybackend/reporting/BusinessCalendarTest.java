package kn.org.deliverybackend.reporting;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The calendar maths every report depends on. If any of these fail, every
 * figure in the admin panel is attributed to the wrong period.
 */
class BusinessCalendarTest {

    /** Bangladesh defaults: Dhaka, Sunday-start week, July fiscal year. */
    private final BusinessCalendar bd = new BusinessCalendar(ZoneId.of("Asia/Dhaka"), DayOfWeek.SUNDAY, 7);

    // ── day ─────────────────────────────────────────────────────────────────

    @Test
    void dayIsASingleLocalDate() {
        ReportPeriod p = bd.period(PeriodType.DAY, LocalDate.of(2026, 9, 4));
        assertEquals(LocalDate.of(2026, 9, 4), p.start());
        assertEquals(LocalDate.of(2026, 9, 5), p.endExclusive());
        assertEquals(LocalDate.of(2026, 9, 4), p.endInclusive());
        assertEquals(1, p.days());
    }

    // ── week (Sunday–Saturday) ──────────────────────────────────────────────

    @Test
    void weekStartsOnSundayForEveryDayOfTheWeek() {
        // 2026-09-06 is a Sunday; 2026-09-12 is the following Saturday.
        LocalDate sunday = LocalDate.of(2026, 9, 6);
        assertEquals(DayOfWeek.SUNDAY, sunday.getDayOfWeek());
        for (int i = 0; i < 7; i++) {
            ReportPeriod p = bd.period(PeriodType.WEEK, sunday.plusDays(i));
            assertEquals(sunday, p.start(), "day offset " + i);
            assertEquals(sunday.plusDays(7), p.endExclusive(), "day offset " + i);
        }
    }

    @Test
    void saturdayBelongsToTheWeekThatStartedOnTheSundayBefore() {
        // Thursday 2026-09-03 → Sunday 2026-08-30 … Saturday 2026-09-05.
        ReportPeriod p = bd.period(PeriodType.WEEK, LocalDate.of(2026, 9, 3));
        assertEquals(LocalDate.of(2026, 8, 30), p.start());
        assertEquals(LocalDate.of(2026, 9, 5), p.endInclusive());
        assertEquals("30 Aug – 5 Sep 2026", bd.label(p));
    }

    @Test
    void mondayStartWeekStillWorksWhenConfigured() {
        BusinessCalendar iso = new BusinessCalendar(ZoneId.of("Asia/Dhaka"), DayOfWeek.MONDAY, 1);
        ReportPeriod p = iso.period(PeriodType.WEEK, LocalDate.of(2026, 9, 6)); // a Sunday
        assertEquals(LocalDate.of(2026, 8, 31), p.start());
        assertEquals(0, iso.weekShiftDays());
    }

    @Test
    void weekShiftMatchesPostgresTruncation() {
        // date_trunc('week') is Monday-based; shifting by these amounts makes it
        // land on the configured start (verified against the SQL in ReportRepository).
        assertEquals(1, bd.weekShiftDays());
        assertEquals(2, new BusinessCalendar(ZoneId.of("Asia/Dhaka"), DayOfWeek.SATURDAY, 7).weekShiftDays());
        assertEquals(0, new BusinessCalendar(ZoneId.of("Asia/Dhaka"), DayOfWeek.MONDAY, 7).weekShiftDays());
    }

    // ── month / year ────────────────────────────────────────────────────────

    @Test
    void monthCoversWholeCalendarMonthIncludingLeapFebruary() {
        ReportPeriod feb = bd.period(PeriodType.MONTH, LocalDate.of(2028, 2, 15));
        assertEquals(LocalDate.of(2028, 2, 1), feb.start());
        assertEquals(LocalDate.of(2028, 2, 29), feb.endInclusive());
        assertEquals(29, feb.days());
        assertEquals("February 2028", bd.label(feb));
    }

    @Test
    void calendarYear() {
        ReportPeriod y = bd.period(PeriodType.YEAR, LocalDate.of(2026, 9, 4));
        assertEquals(LocalDate.of(2026, 1, 1), y.start());
        assertEquals(LocalDate.of(2026, 12, 31), y.endInclusive());
        assertEquals("2026", bd.label(y));
    }

    // ── fiscal year (1 Jul – 30 Jun) ────────────────────────────────────────

    @Test
    void fiscalYearBoundaries() {
        // 30 June 2026 is the last day of FY 2025-26.
        ReportPeriod fy2526 = bd.period(PeriodType.FISCAL_YEAR, LocalDate.of(2026, 6, 30));
        assertEquals(LocalDate.of(2025, 7, 1), fy2526.start());
        assertEquals(LocalDate.of(2026, 6, 30), fy2526.endInclusive());
        assertEquals("FY 2025-26", bd.label(fy2526));

        // 1 July 2026 opens FY 2026-27.
        ReportPeriod fy2627 = bd.period(PeriodType.FISCAL_YEAR, LocalDate.of(2026, 7, 1));
        assertEquals(LocalDate.of(2026, 7, 1), fy2627.start());
        assertEquals(LocalDate.of(2027, 6, 30), fy2627.endInclusive());
        assertEquals("FY 2026-27", bd.label(fy2627));

        // September sits in the fiscal year that started this July.
        assertEquals(fy2627, bd.period(PeriodType.FISCAL_YEAR, LocalDate.of(2026, 9, 4)));
    }

    // ── previous period ─────────────────────────────────────────────────────

    @Test
    void previousPeriodsAreContiguousAndSameType() {
        for (PeriodType type : PeriodType.values()) {
            ReportPeriod current = bd.period(type, LocalDate.of(2026, 9, 4));
            ReportPeriod prev = bd.previous(current);
            assertEquals(type, prev.type());
            assertEquals(current.start(), prev.endExclusive(), type + " must be contiguous");
            assertFalse(prev.contains(current.start()));
            assertTrue(prev.contains(current.start().minusDays(1)));
        }
    }

    @Test
    void previousMonthAcrossYearBoundary() {
        ReportPeriod jan = bd.period(PeriodType.MONTH, LocalDate.of(2027, 1, 15));
        ReportPeriod dec = bd.previous(jan);
        assertEquals(LocalDate.of(2026, 12, 1), dec.start());
        assertEquals(LocalDate.of(2026, 12, 31), dec.endInclusive());
    }

    @Test
    void sameWeekLastYearIsAlsoSundayStart() {
        ReportPeriod thisWeek = bd.period(PeriodType.WEEK, LocalDate.of(2026, 9, 4));
        ReportPeriod lastYear = bd.sameLastYear(thisWeek);
        assertEquals(DayOfWeek.SUNDAY, lastYear.start().getDayOfWeek());
        assertEquals(7, lastYear.days());
    }

    // ── UTC conversion ──────────────────────────────────────────────────────

    @Test
    void dhakaMidnightIsSixPmUtcThePreviousDay() {
        assertEquals(LocalDateTime.of(2026, 9, 3, 18, 0), bd.toUtc(LocalDate.of(2026, 9, 4)));
        assertEquals("2026-09-03 18:00:00", bd.toUtcSql(LocalDate.of(2026, 9, 4)));
    }

    @Test
    void utcRoundTripsThroughLocal() {
        LocalDateTime utc = LocalDateTime.of(2026, 9, 3, 17, 30);
        assertEquals(LocalDateTime.of(2026, 9, 3, 23, 30), bd.fromUtc(utc));
        // An order at 17:30 UTC on the 3rd is a 3 September order in Dhaka …
        assertTrue(bd.period(PeriodType.DAY, LocalDate.of(2026, 9, 3)).contains(bd.fromUtc(utc).toLocalDate()));
        // … while 18:30 UTC already belongs to the 4th.
        assertEquals(LocalDate.of(2026, 9, 4), bd.fromUtc(utc.plusHours(1)).toLocalDate());
    }

    @Test
    void springConstructorParsesConfigStrings() {
        BusinessCalendar c = new BusinessCalendar(" Asia/Dhaka ", "sunday", 7);
        assertEquals(ZoneId.of("Asia/Dhaka"), c.zone());
        assertEquals(DayOfWeek.SUNDAY, c.weekStart());
    }
}
