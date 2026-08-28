package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.report.CustomerLifetimeValueDTO;
import kn.org.deliverybackend.dto.report.RevenuePointDTO;
import kn.org.deliverybackend.dto.report.SalesSummaryDTO;
import kn.org.deliverybackend.dto.report.TopCategoryDTO;
import kn.org.deliverybackend.dto.report.TopProductDTO;
import kn.org.deliverybackend.repository.ReportRepository;
import kn.org.deliverybackend.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;

    @Override
    @Transactional(readOnly = true)
    public SalesSummaryDTO summary(LocalDate from, LocalDate to) {
        Object[] row = reportRepository.salesSummary(startOf(from), endOf(to));
        // PostgreSQL JDBC returns nested array — unwrap if needed.
        Object[] flat = (row != null && row.length == 1 && row[0] instanceof Object[])
                ? (Object[]) row[0] : row;
        long total      = num(flat, 0).longValue();
        long delivered  = num(flat, 1).longValue();
        long cancelled  = num(flat, 2).longValue();
        long returned   = num(flat, 3).longValue();
        BigDecimal gross = bd(flat, 4);
        BigDecimal net   = bd(flat, 5);
        long unique     = num(flat, 6).longValue();

        BigDecimal aov = delivered == 0
                ? BigDecimal.ZERO
                : net.divide(BigDecimal.valueOf(delivered), 2, RoundingMode.HALF_UP);

        return SalesSummaryDTO.builder()
                .totalOrders(total)
                .deliveredOrders(delivered)
                .cancelledOrders(cancelled)
                .returnedOrders(returned)
                .grossRevenue(gross)
                .netRevenue(net)
                .averageOrderValue(aov)
                .uniqueCustomers(unique)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RevenuePointDTO> revenueTimeseries(LocalDate from, LocalDate to, Granularity granularity) {
        LocalDateTime start = startOf(from);
        LocalDateTime end   = endOf(to);
        List<Object[]> rows = switch (granularity) {
            case DAY   -> reportRepository.revenueDaily(start, end);
            case WEEK  -> reportRepository.revenueWeekly(start, end);
            case MONTH -> reportRepository.revenueMonthly(start, end);
        };
        return rows.stream()
                .map(r -> RevenuePointDTO.builder()
                        .date(toDate(r[0]))
                        .revenue(bd(r, 1))
                        .orderCount(num(r, 2).longValue())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopProductDTO> topProducts(LocalDate from, LocalDate to, int limit) {
        return reportRepository.topProducts(startOf(from), endOf(to), capped(limit))
                .stream()
                .map(r -> TopProductDTO.builder()
                        .productId(num(r, 0).longValue())
                        .productName((String) r[1])
                        .imageUrl((String) r[2])
                        .unitsSold(num(r, 3).longValue())
                        .revenue(bd(r, 4))
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopCategoryDTO> topCategories(LocalDate from, LocalDate to, int limit) {
        return reportRepository.topCategories(startOf(from), endOf(to), capped(limit))
                .stream()
                .map(r -> TopCategoryDTO.builder()
                        .categoryId(num(r, 0).longValue())
                        .categoryName((String) r[1])
                        .unitsSold(num(r, 2).longValue())
                        .revenue(bd(r, 3))
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
                            .firstOrderAt(toLdt(r[6]))
                            .lastOrderAt(toLdt(r[7]))
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long totalCustomersWithOrders() {
        return reportRepository.countCustomersWithOrders();
    }

    // ── conversion helpers ──────────────────────────────────────────────────────

    private static LocalDateTime startOf(LocalDate d) {
        return (d == null ? LocalDate.of(1970, 1, 1) : d).atStartOfDay();
    }

    private static LocalDateTime endOf(LocalDate d) {
        return (d == null ? LocalDate.now().plusDays(1) : d.plusDays(1)).atStartOfDay();
    }

    private static int capped(int limit) {
        if (limit <= 0) return 10;
        return Math.min(limit, 100);
    }

    private static Number num(Object[] row, int i) {
        if (row == null || row.length <= i || row[i] == null) return 0L;
        return (Number) row[i];
    }

    private static BigDecimal bd(Object[] row, int i) {
        if (row == null || row.length <= i || row[i] == null) return BigDecimal.ZERO;
        Object v = row[i];
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n)      return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(v.toString());
    }

    private static LocalDate toDate(Object raw) {
        if (raw == null) return null;
        if (raw instanceof java.sql.Date sql) return sql.toLocalDate();
        if (raw instanceof Date sql)          return sql.toLocalDate();
        if (raw instanceof LocalDate ld)       return ld;
        if (raw instanceof Timestamp ts)       return ts.toLocalDateTime().toLocalDate();
        if (raw instanceof java.util.Date u)   return u.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return LocalDate.parse(raw.toString());
    }

    private static LocalDateTime toLdt(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Timestamp ts) return ts.toLocalDateTime();
        if (raw instanceof java.util.Date u)
            return u.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
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
