package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.dto.AnalyticsDTO;
import kn.org.deliverybackend.dto.DashboardStatsDTO;
import kn.org.deliverybackend.dto.report.PeriodReportDTO.Bucket;
import kn.org.deliverybackend.dto.report.PeriodReportDTO.PeriodMetrics;
import kn.org.deliverybackend.dto.report.TopProductDTO;
import kn.org.deliverybackend.dto.response.product.InventorySummaryDTO;
import kn.org.deliverybackend.entity.Product;
import kn.org.deliverybackend.reporting.BusinessCalendar;
import kn.org.deliverybackend.reporting.PeriodType;
import kn.org.deliverybackend.reporting.ReportPeriod;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.UserRiderRepository;
import kn.org.deliverybackend.service.InventoryService;
import kn.org.deliverybackend.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dashboard and analytics endpoints. Both delegate every number to
 * {@link ReportService}, so the dashboard, the analytics page and the
 * Reports page are three views of one set of SQL definitions rather than
 * three separate calculations that can drift apart.
 */
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AdminDashboardController {

    private static final LocalDate EPOCH = LocalDate.of(1970, 1, 1);
    private static final DateTimeFormatter AS_OF = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ReportService reportService;
    private final BusinessCalendar calendar;
    private final UserRiderRepository userRiderRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    @Value("${app.business.currency:BDT}")
    private String currency;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDTO> getStats() {
        LocalDate today = calendar.today();
        ReportPeriod week = calendar.period(PeriodType.WEEK, today);
        ReportPeriod lastWeek = calendar.previous(week);
        ReportPeriod month = calendar.period(PeriodType.MONTH, today);
        ReportPeriod lastMonth = calendar.previous(month);

        PeriodMetrics allTime   = reportService.windowMetrics(EPOCH, today);
        PeriodMetrics todayM    = reportService.windowMetrics(today, today);
        PeriodMetrics yesterday = reportService.windowMetrics(today.minusDays(1), today.minusDays(1));
        PeriodMetrics weekM     = metrics(week);
        PeriodMetrics lastWeekM = metrics(lastWeek);
        PeriodMetrics monthM    = metrics(month);
        PeriodMetrics lastMonthM = metrics(lastMonth);

        long activeRiders = userRiderRepository.findAll().stream()
                .filter(r -> "ACTIVE".equalsIgnoreCase(r.getStatus())).count();
        InventorySummaryDTO inventory = inventoryService.getSummary();

        return ResponseEntity.ok(DashboardStatsDTO.builder()
                .totalOrders(allTime.getPlacedOrders())
                .validOrders(allTime.getOrders())
                .totalRevenue(allTime.getNetRevenue().doubleValue())
                .pendingOrders(allTime.getPendingOrders())
                .cancelledOrders(allTime.getCancelledOrders())
                .activeRiders(activeRiders)
                .todayOrders(todayM.getOrders())
                .todayRevenue(todayM.getNetRevenue().doubleValue())
                .yesterdayOrders(yesterday.getOrders())
                .yesterdayRevenue(yesterday.getNetRevenue().doubleValue())
                .weekOrders(weekM.getOrders())
                .weekRevenue(weekM.getNetRevenue().doubleValue())
                .lastWeekOrders(lastWeekM.getOrders())
                .lastWeekRevenue(lastWeekM.getNetRevenue().doubleValue())
                .monthOrders(monthM.getOrders())
                .monthRevenue(monthM.getNetRevenue().doubleValue())
                .lastMonthOrders(lastMonthM.getOrders())
                .lastMonthRevenue(lastMonthM.getNetRevenue().doubleValue())
                .lowStockProducts(inventory.getCriticalLow())
                .outOfStockProducts(inventory.getOutOfStock())
                .asOf(AS_OF.format(calendar.now()))
                .zone(calendar.zone().getId())
                .currency(currency)
                .build());
    }

    @GetMapping("/analytics")
    public ResponseEntity<AnalyticsDTO> getAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        LocalDate today = calendar.today();
        LocalDate from = fromDate != null ? fromDate : EPOCH;
        LocalDate to = toDate != null ? toDate : today;

        PeriodMetrics m = reportService.windowMetrics(from, to);

        // Orders by status: every placed order in the window.
        List<AnalyticsDTO.OrderStatusCount> byStatus = reportService.ordersByStatus(from, to).stream()
                .map(s -> new AnalyticsDTO.OrderStatusCount(s.getStatus(), s.getCount()))
                .toList();

        // Payment split: counted orders and their net revenue.
        List<AnalyticsDTO.PaymentMethodCount> byPayment = reportService.ordersByPayment(from, to).stream()
                .map(p -> new AnalyticsDTO.PaymentMethodCount(p.getMethod(), p.getOrders(), p.getRevenue().doubleValue()))
                .toList();

        // Trend: the last seven business days ending at the window's end,
        // zero-filled so a quiet day is drawn as zero rather than skipped.
        LocalDate trendEnd = to.isAfter(today) ? today : to;
        LocalDate trendStart = trendEnd.minusDays(6);
        if (trendStart.isBefore(from)) trendStart = from;
        List<AnalyticsDTO.DailyOrderCount> daily = reportService
                .series(trendStart, trendEnd, ReportService.Granularity.DAY).stream()
                .map(b -> new AnalyticsDTO.DailyOrderCount(
                        b.getBucketStart().toLocalDate().toString(), b.getOrders(), b.getNetRevenue().doubleValue()))
                .toList();

        List<AnalyticsDTO.CategoryRevenue> topCategories = reportService.topCategories(from, to, 6).stream()
                .map(c -> new AnalyticsDTO.CategoryRevenue(c.getCategoryName(), c.getRevenue().doubleValue(), c.getOrderCount()))
                .toList();

        // Best sellers in the window, decorated with current price and stock.
        List<TopProductDTO> best = reportService.topProducts(from, to, 5);
        Map<Long, Product> products = productRepository.findAllById(
                        best.stream().map(TopProductDTO::getProductId).toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<AnalyticsDTO.TopProduct> topProducts = best.stream()
                .map(t -> {
                    Product p = products.get(t.getProductId());
                    int stock = p != null ? p.getStockQuantity() : 0;
                    double price = p != null && p.getPrice() != null ? p.getPrice().doubleValue() : 0;
                    return new AnalyticsDTO.TopProduct(
                            t.getProductId(), t.getProductName(), t.getImageUrl(), price, stock,
                            stock == 0 ? "OUT_OF_STOCK" : stock <= 10 ? "LOW_STOCK" : "IN_STOCK");
                })
                .toList();

        // Riders are a legacy of the delivery-app era; kept for API compatibility.
        var allRiders = userRiderRepository.findAll();
        long activeRiders = allRiders.stream().filter(r -> "ACTIVE".equalsIgnoreCase(r.getStatus())).count();
        long totalRiders = allRiders.size();
        double avgRiderRating = Math.round(allRiders.stream()
                .filter(r -> r.getRating() != null && r.getRating() > 0)
                .mapToDouble(r -> r.getRating()).average().orElse(0.0) * 10.0) / 10.0;
        List<AnalyticsDTO.RiderStat> topRiders = allRiders.stream().limit(5)
                .map(r -> new AnalyticsDTO.RiderStat(
                        r.getId() != null ? r.getId().toString() : "",
                        r.getName(), r.getImageUrl(), r.getStatus(),
                        r.getRating() != null ? r.getRating() : 0.0, 0L))
                .toList();

        InventorySummaryDTO inventory = inventoryService.getSummary();

        return ResponseEntity.ok(new AnalyticsDTO(
                m.getNetRevenue().doubleValue(),
                m.getPlacedOrders(),
                activeRiders,
                m.getCancelledOrders(),
                m.getDeliveredOrders(),
                m.getPendingOrders(),
                m.getDeliveryRate(),
                m.getCancellationRate(),
                m.getAverageOrderValue().doubleValue(),
                inventory.getCriticalLow(), inventory.getOutOfStock(), inventory.getReorderPending(),
                totalRiders, totalRiders - activeRiders, avgRiderRating,
                byStatus, byPayment, daily, topCategories, topProducts, topRiders
        ));
    }

    private PeriodMetrics metrics(ReportPeriod period) {
        return reportService.windowMetrics(period.start(), period.endInclusive());
    }
}
