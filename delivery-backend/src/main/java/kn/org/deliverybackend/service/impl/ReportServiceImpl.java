package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.report.CustomerLifetimeValueDTO;
import kn.org.deliverybackend.dto.report.PeriodReportDTO;
import kn.org.deliverybackend.dto.report.PeriodReportDTO.Bucket;
import kn.org.deliverybackend.dto.report.PeriodReportDTO.PaymentSplit;
import kn.org.deliverybackend.dto.report.PeriodReportDTO.PeriodMetrics;
import kn.org.deliverybackend.dto.report.PeriodReportDTO.StatusCount;
import kn.org.deliverybackend.dto.report.RevenuePointDTO;
import kn.org.deliverybackend.dto.report.SalesSummaryDTO;
import kn.org.deliverybackend.dto.report.TopCategoryDTO;
import kn.org.deliverybackend.dto.report.TopProductDTO;
import kn.org.deliverybackend.enumeration.OrderStatus;
import kn.org.deliverybackend.exception.InvalidStockOperationException;
import kn.org.deliverybackend.reporting.BusinessCalendar;
import kn.org.deliverybackend.reporting.PeriodType;
import kn.org.deliverybackend.reporting.ReportPeriod;
import kn.org.deliverybackend.repository.ReportRepository;
import kn.org.deliverybackend.service.ReportService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Turns {@link ReportRepository} rows into report DTOs.
 *
 * <p>All window arithmetic goes through {@link BusinessCalendar}: callers pass
 * business-local dates, this class converts them to UTC strings for SQL and
 * converts bucket timestamps back. Nothing here touches the JVM default zone.
 */
@Service
public class ReportServiceImpl implements ReportService {

    /** "From the beginning" for the legacy endpoints that accept a null start. */
    private static final LocalDate EPOCH = LocalDate.of(1970, 1, 1);

    private static final DateTimeFormatter BUCKET_TS  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_LABEL  = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter WEEK_LABEL = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter YEAR_LABEL = DateTimeFormatter.ofPattern("yyyy");

    private final ReportRepository reportRepository;
    private final BusinessCalendar calendar;
    private final String currency;

    public ReportServiceImpl(ReportRepository reportRepository,
                             BusinessCalendar calendar,
                             @Value("${app.business.currency:BDT}") String currency) {
        this.reportRepository = reportRepository;
        this.calendar = calendar;
        this.currency = currency;
    }

    // ── period report ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PeriodReportDTO periodReport(PeriodType type, LocalDate anchor) {
        ReportPeriod period = calendar.period(type, anchor);
        ReportPeriod previous = calendar.previous(period);

        return PeriodReportDTO.builder()
                .type(type.name())
                .label(calendar.label(period))
                .start(period.start())
                .end(period.endInclusive())
                .zone(calendar.zone().getId())
                .weekStart(calendar.weekStart().name())
                .currency(currency)
                .generatedAt(calendar.now())
                .complete(!period.endExclusive().isAfter(calendar.today()))
                .current(metrics(period.start(), period.endExclusive()))
                .previous(metrics(previous.start(), previous.endExclusive()))
                .previousLabel(calendar.label(previous))
                .previousStart(previous.start())
                .previousEnd(previous.endInclusive())
                .bucketUnit(type.bucketUnit())
                .breakdown(bucketSeries(period.start(), period.endExclusive(), type.bucketUnit()))
                .byStatus(statusCounts(period.start(), period.endExclusive()))
                .byPayment(paymentSplits(period.start(), period.endExclusive()))
                .topProducts(topProducts(period.start(), period.endInclusive(), 10))
                .topCategories(topCategories(period.start(), period.endInclusive(), 10))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PeriodMetrics windowMetrics(LocalDate from, LocalDate toInclusive) {
        LocalDate start = fromOrEpoch(from);
        LocalDate end = toOrToday(toInclusive);
        checkOrder(start, end);
        return metrics(start, end.plusDays(1));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Bucket> series(LocalDate from, LocalDate toInclusive, Granularity granularity) {
        LocalDate start = fromOrEpoch(from);
        LocalDate end = toOrToday(toInclusive);
        checkOrder(start, end);
        return bucketSeries(start, end.plusDays(1), granularity.name().toLowerCase(Locale.ROOT));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StatusCount> ordersByStatus(LocalDate from, LocalDate toInclusive) {
        LocalDate start = fromOrEpoch(from);
        LocalDate end = toOrToday(toInclusive);
        checkOrder(start, end);
        return statusCounts(start, end.plusDays(1));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentSplit> ordersByPayment(LocalDate from, LocalDate toInclusive) {
        LocalDate start = fromOrEpoch(from);
        LocalDate end = toOrToday(toInclusive);
        checkOrder(start, end);
        return paymentSplits(start, end.plusDays(1));
    }

    // ── legacy-shaped endpoints, now on the same engine ─────────────────────

    @Override
    @Transactional(readOnly = true)
    public SalesSummaryDTO summary(LocalDate from, LocalDate to) {
        PeriodMetrics m = windowMetrics(from, to);
        return SalesSummaryDTO.builder()
                .totalOrders(m.getPlacedOrders())
                .deliveredOrders(m.getDeliveredOrders())
                .cancelledOrders(m.getCancelledOrders())
                .returnedOrders(m.getReturnedOrders())
                // gross here keeps its historical meaning: every placed order's total
                .grossRevenue(m.getNetRevenue().add(m.getCancelledValue()).add(m.getReturnedValue()))
                .netRevenue(m.getNetRevenue())
                .averageOrderValue(m.getAverageOrderValue())
                .uniqueCustomers(m.getUniqueCustomers())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RevenuePointDTO> revenueTimeseries(LocalDate from, LocalDate to, Granularity granularity) {
        return series(from, to, granularity).stream()
                .map(b -> RevenuePointDTO.builder()
                        .date(b.getBucketStart().toLocalDate())
                        .revenue(b.getNetRevenue())
                        .orderCount(b.getOrders())
                        .cancelledOrders(b.getCancelledOrders())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopProductDTO> topProducts(LocalDate from, LocalDate to, int limit) {
        LocalDate start = fromOrEpoch(from);
        LocalDate end = toOrToday(to);
        checkOrder(start, end);
        return reportRepository.topProducts(utc(start), utc(end.plusDays(1)), capped(limit))
                .stream()
                .map(r -> TopProductDTO.builder()
                        .productId(num(r, 0).longValue())
                        .productName((String) r[1])
                        .imageUrl((String) r[2])
                        .unitsSold(num(r, 3).longValue())
                        .revenue(bd(r, 4))
                        .orderCount(num(r, 5).longValue())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopCategoryDTO> topCategories(LocalDate from, LocalDate to, int limit) {
        LocalDate start = fromOrEpoch(from);
        LocalDate end = toOrToday(to);
        checkOrder(start, end);
        return reportRepository.topCategories(utc(start), utc(end.plusDays(1)), capped(limit))
                .stream()
                .map(r -> TopCategoryDTO.builder()
                        .categoryId(num(r, 0).longValue())
                        .categoryName((String) r[1])
                        .unitsSold(num(r, 2).longValue())
                        .revenue(bd(r, 3))
                        .orderCount(num(r, 4).longValue())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerLifetimeValueDTO> customerLtv(int limit, int offset) {
        return reportRepository.customerLifetimeValue(capped(limit), Math.max(0, offset))
                .stream()
                .map(r -> {
                    long count = num(r, 4).longValue();
                    BigDecimal revenue = bd(r, 5);
                    BigDecimal aov = count == 0 ? BigDecimal.ZERO
                            : revenue.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
                    return CustomerLifetimeValueDTO.builder()
                            .userId(toUuid(r[0]))
                            .customerName(joinName((String) r[1], (String) r[2]))
                            .email((String) r[3])
                            .orderCount(count)
                            .lifetimeRevenue(revenue)
                            .averageOrderValue(aov)
                            .firstOrderAt(toLocal(r[6]))
                            .lastOrderAt(toLocal(r[7]))
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long totalCustomersWithOrders() {
        return reportRepository.countCustomersWithOrders();
    }

    // ── engine ──────────────────────────────────────────────────────────────

    /** Metrics for the half-open local window {@code [start, endExclusive)}. */
    private PeriodMetrics metrics(LocalDate start, LocalDate endExclusive) {
        String from = utc(start);
        String to = utc(endExclusive);

        Object[] r = firstRow(reportRepository.windowMetrics(from, to));
        long placed    = num(r, 0).longValue();
        long orders    = num(r, 1).longValue();
        long delivered = num(r, 2).longValue();
        long cancelled = num(r, 3).longValue();
        long returned  = num(r, 4).longValue();
        BigDecimal net = bd(r, 11);

        Object[] d = firstRow(reportRepository.deliveredInWindow(from, to));
        Object[] c = firstRow(reportRepository.cancelledInWindow(from, to));

        return PeriodMetrics.builder()
                .placedOrders(placed)
                .orders(orders)
                .deliveredOrders(delivered)
                .cancelledOrders(cancelled)
                .returnedOrders(returned)
                .pendingOrders(num(r, 5).longValue())
                .inProgressOrders(num(r, 6).longValue())
                .unitsSold(num(r, 17).longValue())
                .grossSales(bd(r, 7))
                .discounts(bd(r, 8))
                .shipping(bd(r, 9))
                .surcharges(bd(r, 16))
                .vat(bd(r, 10))
                .netRevenue(net)
                .deliveredRevenue(bd(r, 12))
                .averageOrderValue(orders == 0 ? money(BigDecimal.ZERO)
                        : net.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP))
                .cancelledValue(bd(r, 13))
                .returnedValue(bd(r, 14))
                .uniqueCustomers(num(r, 15).longValue())
                .newCustomers(reportRepository.newCustomers(from, to))
                .cancellationRate(pct(cancelled, placed))
                .returnRate(pct(returned, placed))
                .deliveryRate(pct(delivered, placed))
                .deliveredInPeriodOrders(num(d, 0).longValue())
                .deliveredInPeriodRevenue(bd(d, 1))
                .cancelledInPeriodOrders(num(c, 0).longValue())
                .cancelledInPeriodValue(bd(c, 1))
                .build();
    }

    /**
     * Bucketed series over {@code [start, endExclusive)}, with every expected
     * bucket present even when it had no orders, so charts show the quiet
     * hours and days rather than skipping them.
     */
    private List<Bucket> bucketSeries(LocalDate start, LocalDate endExclusive, String unit) {
        List<Object[]> rows = reportRepository.buckets(
                utc(start), utc(endExclusive), calendar.zone().getId(), unit, calendar.weekShiftDays());

        // Sorted so a bucket the generator did not anticipate is still
        // reported in order rather than dropped.
        TreeMap<LocalDateTime, Bucket> byStart = new TreeMap<>();
        for (LocalDateTime expected : expectedBucketStarts(start, endExclusive, unit)) {
            byStart.put(expected, emptyBucket(expected, unit));
        }
        for (Object[] r : rows) {
            LocalDateTime bucketStart = LocalDateTime.parse(String.valueOf(r[0]), BUCKET_TS);
            byStart.put(bucketStart, Bucket.builder()
                    .bucketStart(bucketStart)
                    .label(bucketLabel(bucketStart, unit))
                    .placedOrders(num(r, 1).longValue())
                    .orders(num(r, 2).longValue())
                    .cancelledOrders(num(r, 3).longValue())
                    .netRevenue(bd(r, 4))
                    .build());
        }
        return new ArrayList<>(byStart.values());
    }

    private List<LocalDateTime> expectedBucketStarts(LocalDate start, LocalDate endExclusive, String unit) {
        List<LocalDateTime> out = new ArrayList<>();
        LocalDateTime end = endExclusive.atStartOfDay();
        switch (unit) {
            case "hour" -> {
                for (LocalDateTime t = start.atStartOfDay(); t.isBefore(end); t = t.plusHours(1)) out.add(t);
            }
            case "day" -> {
                for (LocalDate d = start; d.isBefore(endExclusive); d = d.plusDays(1)) out.add(d.atStartOfDay());
            }
            case "week" -> {
                LocalDate first = calendar.period(PeriodType.WEEK, start).start();
                for (LocalDate d = first; d.isBefore(endExclusive); d = d.plusWeeks(1)) out.add(d.atStartOfDay());
            }
            case "month" -> {
                for (LocalDate d = start.withDayOfMonth(1); d.isBefore(endExclusive); d = d.plusMonths(1)) out.add(d.atStartOfDay());
            }
            case "year" -> {
                for (LocalDate d = start.withDayOfYear(1); d.isBefore(endExclusive); d = d.plusYears(1)) out.add(d.atStartOfDay());
            }
            default -> throw new InvalidStockOperationException("Unknown bucket unit: " + unit);
        }
        return out;
    }

    private Bucket emptyBucket(LocalDateTime start, String unit) {
        return Bucket.builder()
                .bucketStart(start)
                .label(bucketLabel(start, unit))
                .placedOrders(0)
                .orders(0)
                .cancelledOrders(0)
                .netRevenue(money(BigDecimal.ZERO))
                .build();
    }

    private static String bucketLabel(LocalDateTime start, String unit) {
        return switch (unit) {
            case "hour" -> HOUR_LABEL.format(start);
            case "day" -> DAY_LABEL.format(start);
            case "week" -> WEEK_LABEL.format(start);
            case "month" -> MONTH_LABEL.format(start);
            default -> YEAR_LABEL.format(start);
        };
    }

    private List<StatusCount> statusCounts(LocalDate start, LocalDate endExclusive) {
        // Legacy PENDING rows are the same business state as PLACED; merge them
        // so the chart does not show two bars for one status.
        Map<String, StatusCount> merged = new LinkedHashMap<>();
        for (Object[] r : reportRepository.ordersByStatus(utc(start), utc(endExclusive))) {
            String raw = r[0] == null ? "UNKNOWN" : String.valueOf(r[0]);
            String status = OrderStatus.parse(raw).map(s -> s.normalize().name()).orElse(raw);
            StatusCount existing = merged.get(status);
            long count = num(r, 1).longValue();
            BigDecimal value = bd(r, 2);
            if (existing == null) {
                merged.put(status, StatusCount.builder().status(status).count(count).value(value).build());
            } else {
                existing.setCount(existing.getCount() + count);
                existing.setValue(existing.getValue().add(value));
            }
        }
        List<StatusCount> out = new ArrayList<>(merged.values());
        out.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));
        return out;
    }

    private List<PaymentSplit> paymentSplits(LocalDate start, LocalDate endExclusive) {
        // "COD" and "CASH" are both cash on delivery (older rows used COD);
        // merge them so the split does not show the same channel twice.
        Map<String, PaymentSplit> merged = new LinkedHashMap<>();
        for (Object[] r : reportRepository.ordersByPayment(utc(start), utc(endExclusive))) {
            String method = normalizePaymentMethod(r[0]);
            PaymentSplit row = PaymentSplit.builder()
                    .method(method)
                    .orders(num(r, 1).longValue())
                    .revenue(bd(r, 2))
                    .deliveredOrders(num(r, 3).longValue())
                    .deliveredRevenue(bd(r, 4))
                    .undeliveredOrders(num(r, 5).longValue())
                    .undeliveredValue(bd(r, 6))
                    .cancelledOrders(num(r, 7).longValue())
                    .build();
            PaymentSplit existing = merged.get(method);
            if (existing == null) {
                merged.put(method, row);
            } else {
                existing.setOrders(existing.getOrders() + row.getOrders());
                existing.setRevenue(existing.getRevenue().add(row.getRevenue()));
                existing.setDeliveredOrders(existing.getDeliveredOrders() + row.getDeliveredOrders());
                existing.setDeliveredRevenue(existing.getDeliveredRevenue().add(row.getDeliveredRevenue()));
                existing.setUndeliveredOrders(existing.getUndeliveredOrders() + row.getUndeliveredOrders());
                existing.setUndeliveredValue(existing.getUndeliveredValue().add(row.getUndeliveredValue()));
                existing.setCancelledOrders(existing.getCancelledOrders() + row.getCancelledOrders());
            }
        }
        List<PaymentSplit> out = new ArrayList<>(merged.values());
        out.sort((a, b) -> b.getRevenue().compareTo(a.getRevenue()));
        return out;
    }

    private static String normalizePaymentMethod(Object raw) {
        if (raw == null) return "UNKNOWN";
        String m = String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
        if (m.isEmpty()) return "UNKNOWN";
        return switch (m) {
            case "COD", "CASH_ON_DELIVERY" -> "CASH";
            default -> m;
        };
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private String utc(LocalDate localDate) {
        return calendar.toUtcSql(localDate);
    }

    private LocalDate fromOrEpoch(LocalDate d) {
        return d == null ? EPOCH : d;
    }

    private LocalDate toOrToday(LocalDate d) {
        return d == null ? calendar.today() : d;
    }

    private static void checkOrder(LocalDate from, LocalDate toInclusive) {
        if (toInclusive.isBefore(from)) {
            throw new InvalidStockOperationException("'to' (" + toInclusive + ") is before 'from' (" + from + ")");
        }
    }

    private static int capped(int limit) {
        if (limit <= 0) return 10;
        return Math.min(limit, 100);
    }

    /** Percentage with one decimal, 0 when the denominator is 0. */
    private static double pct(long part, long whole) {
        if (whole == 0) return 0.0;
        return Math.round(part * 1000.0 / whole) / 10.0;
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** Native aggregate queries come back as a one-row list, sometimes nested. */
    private static Object[] firstRow(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) return new Object[0];
        Object[] row = rows.get(0);
        if (row != null && row.length == 1 && row[0] instanceof Object[] nested) return nested;
        return row;
    }

    private static Number num(Object[] row, int i) {
        if (row == null || row.length <= i || row[i] == null) return 0L;
        Object v = row[i];
        if (v instanceof Number n) return n;
        if (v instanceof Boolean b) return b ? 1L : 0L;
        return new BigDecimal(v.toString());
    }

    private static BigDecimal bd(Object[] row, int i) {
        if (row == null || row.length <= i || row[i] == null) return money(BigDecimal.ZERO);
        Object v = row[i];
        if (v instanceof BigDecimal b) return money(b);
        if (v instanceof Number n) return money(new BigDecimal(n.toString()));
        return money(new BigDecimal(v.toString()));
    }

    private static LocalDateTime toLocal(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Timestamp ts) return ts.toLocalDateTime();
        if (raw instanceof LocalDateTime ldt) return ldt;
        if (raw instanceof java.util.Date u) return u.toInstant().atZone(ZoneId.of("UTC")).toLocalDateTime();
        return LocalDateTime.parse(raw.toString());
    }

    private static UUID toUuid(Object raw) {
        if (raw == null) return null;
        if (raw instanceof UUID u) return u;
        return UUID.fromString(raw.toString());
    }

    private static String joinName(String first, String last) {
        String f = first == null ? "" : first.trim();
        String l = last == null ? "" : last.trim();
        String combined = (f + " " + l).trim();
        return combined.isEmpty() ? null : combined;
    }
}
