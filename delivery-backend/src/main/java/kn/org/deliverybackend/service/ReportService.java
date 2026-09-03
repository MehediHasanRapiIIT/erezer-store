package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.report.CustomerLifetimeValueDTO;
import kn.org.deliverybackend.dto.report.PeriodReportDTO;
import kn.org.deliverybackend.dto.report.RevenuePointDTO;
import kn.org.deliverybackend.dto.report.SalesSummaryDTO;
import kn.org.deliverybackend.dto.report.TopCategoryDTO;
import kn.org.deliverybackend.dto.report.TopProductDTO;
import kn.org.deliverybackend.reporting.PeriodType;

import java.time.LocalDate;
import java.util.List;

/**
 * Business reporting. All dates are business-local calendar dates
 * ({@code BusinessCalendar}); {@code to} parameters are inclusive.
 */
public interface ReportService {

    enum Granularity { DAY, WEEK, MONTH, YEAR }

    /** The full day / week / month / year / fiscal-year report containing {@code anchor}. */
    PeriodReportDTO periodReport(PeriodType type, LocalDate anchor);

    /** Metrics for an arbitrary date window; the building block for dashboard tiles. */
    PeriodReportDTO.PeriodMetrics windowMetrics(LocalDate from, LocalDate toInclusive);

    /** Zero-filled series across the window in the given unit (day / week / month / year). */
    List<PeriodReportDTO.Bucket> series(LocalDate from, LocalDate toInclusive, Granularity granularity);

    List<PeriodReportDTO.StatusCount> ordersByStatus(LocalDate from, LocalDate toInclusive);

    List<PeriodReportDTO.PaymentSplit> ordersByPayment(LocalDate from, LocalDate toInclusive);

    SalesSummaryDTO summary(LocalDate from, LocalDate to);

    List<RevenuePointDTO> revenueTimeseries(LocalDate from, LocalDate to, Granularity granularity);

    List<TopProductDTO> topProducts(LocalDate from, LocalDate to, int limit);

    List<TopCategoryDTO> topCategories(LocalDate from, LocalDate to, int limit);

    /** Customer LTV across the whole order history (date filter is intentionally absent). */
    List<CustomerLifetimeValueDTO> customerLtv(int limit, int offset);

    long totalCustomersWithOrders();
}
