package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.dto.AnalyticsDTO;
import kn.org.deliverybackend.dto.DashboardStatsDTO;
import kn.org.deliverybackend.entity.Order;
import kn.org.deliverybackend.entity.OrderItem;
import kn.org.deliverybackend.repository.CategoryRepository;
import kn.org.deliverybackend.repository.OrderItemRepository;
import kn.org.deliverybackend.repository.OrderRepository;
import kn.org.deliverybackend.repository.ProductRepository;
import kn.org.deliverybackend.repository.UserRiderRepository;
import kn.org.deliverybackend.service.InventoryService;
import kn.org.deliverybackend.dto.response.product.InventorySummaryDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AdminDashboardController {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRiderRepository userRiderRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryService inventoryService;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDTO> getStats() {
        long totalOrders = orderRepository.findAllOrders().size();
        double totalRevenue = orderRepository.findAllOrders().stream()
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount().doubleValue() : 0).sum();
        long activeRiders = userRiderRepository.findAll().stream()
                .filter(r -> "ACTIVE".equalsIgnoreCase(r.getStatus())).count();
        long pendingOrders = orderRepository.findByOrderStatus("PENDING").size();
        return ResponseEntity.ok(new DashboardStatsDTO(totalOrders, totalRevenue, activeRiders, pendingOrders));
    }

    @GetMapping("/analytics")
    public ResponseEntity<AnalyticsDTO> getAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        // Filter orders by date range if provided
        List<Order> allOrders = orderRepository.findAllOrders().stream()
                .filter(o -> {
                    if (o.getCreatedAt() == null) return true;
                    LocalDate orderDate = o.getCreatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    if (fromDate != null && orderDate.isBefore(fromDate)) return false;
                    if (toDate != null && orderDate.isAfter(toDate)) return false;
                    return true;
                }).collect(Collectors.toList());

        // Core KPIs
        long totalOrders = allOrders.size();
        double totalRevenue = allOrders.stream()
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount().doubleValue() : 0).sum();
        long completedOrders = allOrders.stream()
                .filter(o -> "DELIVERED".equalsIgnoreCase(o.getOrderStatus()) || "COMPLETED".equalsIgnoreCase(o.getOrderStatus())).count();
        long cancelledOrders = allOrders.stream()
                .filter(o -> "CANCELLED".equalsIgnoreCase(o.getOrderStatus())).count();
        long pendingOrders = allOrders.stream()
                .filter(o -> "PENDING".equalsIgnoreCase(o.getOrderStatus())).count();

        // Derived KPIs
        double completionRate = totalOrders > 0 ? Math.round((completedOrders * 100.0 / totalOrders) * 10.0) / 10.0 : 0;
        double cancellationRate = totalOrders > 0 ? Math.round((cancelledOrders * 100.0 / totalOrders) * 10.0) / 10.0 : 0;
        double avgOrderValue = totalOrders > 0 ? Math.round((totalRevenue / totalOrders) * 100.0) / 100.0 : 0;

        // Rider stats
        var allRiders = userRiderRepository.findAll();
        long activeRiders = allRiders.stream().filter(r -> "ACTIVE".equalsIgnoreCase(r.getStatus())).count();
        long totalRiders = allRiders.size();
        long inactiveRiders = totalRiders - activeRiders;
        double avgRiderRating = allRiders.stream()
                .filter(r -> r.getRating() != null && r.getRating() > 0)
                .mapToDouble(r -> r.getRating())
                .average().orElse(0.0);
        avgRiderRating = Math.round(avgRiderRating * 10.0) / 10.0;

        // Inventory health
        InventorySummaryDTO invSummary = inventoryService.getSummary();

        // Orders by status
        Map<String, Long> statusMap = allOrders.stream()
                .collect(Collectors.groupingBy(o -> o.getOrderStatus() != null ? o.getOrderStatus() : "UNKNOWN", Collectors.counting()));
        List<AnalyticsDTO.OrderStatusCount> byStatus = statusMap.entrySet().stream()
                .map(e -> new AnalyticsDTO.OrderStatusCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(AnalyticsDTO.OrderStatusCount::getCount).reversed())
                .collect(Collectors.toList());

        // Orders by payment method
        Map<String, List<Order>> paymentMap = allOrders.stream()
                .collect(Collectors.groupingBy(o -> o.getPaymentMethod() != null ? o.getPaymentMethod() : "UNKNOWN"));
        List<AnalyticsDTO.PaymentMethodCount> byPayment = paymentMap.entrySet().stream()
                .map(e -> new AnalyticsDTO.PaymentMethodCount(
                        e.getKey(), e.getValue().size(),
                        e.getValue().stream().mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount().doubleValue() : 0).sum()
                )).sorted(Comparator.comparingDouble(AnalyticsDTO.PaymentMethodCount::getRevenue).reversed())
                .collect(Collectors.toList());

        // Daily orders — last 7 days
        LocalDate today = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        List<AnalyticsDTO.DailyOrderCount> dailyOrders = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            List<Order> dayOrders = allOrders.stream()
                    .filter(o -> o.getCreatedAt() != null &&
                            o.getCreatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().equals(day))
                    .collect(Collectors.toList());
            double dayRevenue = dayOrders.stream()
                    .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount().doubleValue() : 0).sum();
            dailyOrders.add(new AnalyticsDTO.DailyOrderCount(day.format(fmt), dayOrders.size(), dayRevenue));
        }

        // Top categories by revenue (via OrderItem → Product → Category)
        List<OrderItem> allItems = orderItemRepository.findAll().stream()
                .filter(oi -> oi.getProductId() != null).collect(Collectors.toList());
        Map<Long, Double> productRevenue = new HashMap<>();
        for (OrderItem item : allItems) {
            double rev = item.getPriceAtOrder() != null ? item.getPriceAtOrder().doubleValue() * item.getQuantity() : 0;
            productRevenue.merge(item.getProductId(), rev, Double::sum);
        }
        Map<String, double[]> categoryStats = new HashMap<>(); // [revenue, count]
        for (Map.Entry<Long, Double> entry : productRevenue.entrySet()) {
            productRepository.findById(entry.getKey()).ifPresent(product -> {
                if (product.getCategoryId() != null) {
                    categoryRepository.findById(product.getCategoryId()).ifPresent(cat -> {
                        categoryStats.merge(cat.getName(),
                                new double[]{entry.getValue(), 1},
                                (a, b) -> new double[]{a[0] + b[0], a[1] + b[1]});
                    });
                }
            });
        }
        List<AnalyticsDTO.CategoryRevenue> topCategories = categoryStats.entrySet().stream()
                .map(e -> new AnalyticsDTO.CategoryRevenue(e.getKey(), e.getValue()[0], (long) e.getValue()[1]))
                .sorted(Comparator.comparingDouble(AnalyticsDTO.CategoryRevenue::getRevenue).reversed())
                .limit(6)
                .collect(Collectors.toList());

        // Top products
        List<AnalyticsDTO.TopProduct> topProducts = productRepository.findAll().stream()
                .limit(5)
                .map(p -> new AnalyticsDTO.TopProduct(
                        p.getId(), p.getName(), p.getImageUrl(),
                        p.getPrice() != null ? p.getPrice().doubleValue() : 0,
                        p.getStockQuantity(),
                        p.getStockQuantity() == 0 ? "OUT_OF_STOCK" : p.getStockQuantity() <= 10 ? "LOW_STOCK" : "IN_STOCK"
                )).collect(Collectors.toList());

        // Top riders
        List<AnalyticsDTO.RiderStat> topRiders = allRiders.stream()
                .limit(5)
                .map(r -> new AnalyticsDTO.RiderStat(
                        r.getId() != null ? r.getId().toString() : "",
                        r.getName(), r.getImageUrl(), r.getStatus(),
                        r.getRating() != null ? r.getRating() : 0.0, 0L
                )).collect(Collectors.toList());

        return ResponseEntity.ok(new AnalyticsDTO(
                totalRevenue, totalOrders, activeRiders, cancelledOrders, completedOrders, pendingOrders,
                completionRate, cancellationRate, avgOrderValue,
                invSummary.getCriticalLow(), invSummary.getOutOfStock(), invSummary.getReorderPending(),
                totalRiders, inactiveRiders, avgRiderRating,
                byStatus, byPayment, dailyOrders, topCategories, topProducts, topRiders
        ));
    }
}
