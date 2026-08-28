package kn.org.deliverybackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsDTO {
    // Core KPIs
    private double totalRevenue;
    private long totalOrders;
    private long activeRiders;
    private long cancelledOrders;
    private long completedOrders;
    private long pendingOrders;

    // Derived KPIs
    private double completionRate;      // completedOrders / totalOrders * 100
    private double cancellationRate;    // cancelledOrders / totalOrders * 100
    private double avgOrderValue;       // totalRevenue / totalOrders

    // Inventory health
    private int stockCriticalLow;
    private int stockOutOfStock;
    private int stockReorderPending;

    // Rider distribution
    private long totalRiders;
    private long inactiveRiders;
    private double avgRiderRating;

    // Breakdowns
    private List<OrderStatusCount> ordersByStatus;
    private List<PaymentMethodCount> ordersByPayment;
    private List<DailyOrderCount> dailyOrders;       // last 7 days
    private List<CategoryRevenue> topCategories;
    private List<TopProduct> topProducts;
    private List<RiderStat> topRiders;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class OrderStatusCount {
        private String status;
        private long count;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PaymentMethodCount {
        private String method;
        private long count;
        private double revenue;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class DailyOrderCount {
        private String date;   // yyyy-MM-dd
        private long count;
        private double revenue;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CategoryRevenue {
        private String categoryName;
        private double revenue;
        private long orderCount;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class TopProduct {
        private Long id;
        private String name;
        private String imageUrl;
        private double price;
        private int stockQuantity;
        private String stockStatus;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class RiderStat {
        private String id;
        private String name;
        private String imageUrl;
        private String status;
        private double rating;
        private long totalDeliveries;
    }
}
