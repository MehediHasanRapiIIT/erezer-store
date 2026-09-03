package kn.org.deliverybackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Headline tiles for the admin dashboard. Every figure comes from the same
 * reporting engine as the Reports page, so the two never disagree.
 *
 * <p>Revenue means net revenue of orders that are not cancelled or returned.
 * "Today", "this week" and "this month" are business-local (Asia/Dhaka)
 * periods; the week starts on Sunday.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDTO {

    // All time
    private long totalOrders;        // every placed order, including cancelled
    private long validOrders;        // not cancelled / returned
    private double totalRevenue;
    private long pendingOrders;      // PLACED, awaiting acceptance (any date)
    private long cancelledOrders;
    private long activeRiders;

    // Today vs yesterday
    private long todayOrders;
    private double todayRevenue;
    private long yesterdayOrders;
    private double yesterdayRevenue;

    // This week (Sunday–Saturday) vs last week
    private long weekOrders;
    private double weekRevenue;
    private long lastWeekOrders;
    private double lastWeekRevenue;

    // This month vs last month
    private long monthOrders;
    private double monthRevenue;
    private long lastMonthOrders;
    private double lastMonthRevenue;

    // Inventory
    private int lowStockProducts;
    private int outOfStockProducts;

    // Provenance
    private String asOf;             // business-local timestamp
    private String zone;
    private String currency;
}
