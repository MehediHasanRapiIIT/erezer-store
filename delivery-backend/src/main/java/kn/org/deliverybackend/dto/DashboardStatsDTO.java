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
    private Double totalRevenue;
    private long pendingOrders;      // PLACED, awaiting acceptance (any date)
    private long cancelledOrders;
    private long activeRiders;

    // Today vs yesterday
    private long todayOrders;
    private Double todayRevenue;
    private long yesterdayOrders;
    private Double yesterdayRevenue;

    // This week (Sunday–Saturday) vs last week
    private long weekOrders;
    private Double weekRevenue;
    private long lastWeekOrders;
    private Double lastWeekRevenue;

    // This month vs last month
    private long monthOrders;
    private Double monthRevenue;
    private long lastMonthOrders;
    private Double lastMonthRevenue;

    // Inventory
    private int lowStockProducts;
    private int outOfStockProducts;

    // Provenance
    private String asOf;             // business-local timestamp
    private String zone;
    private String currency;

    /** Leaves out every money total, for staff without "See money totals". */
    public DashboardStatsDTO withoutRevenue() {
        totalRevenue = null;
        todayRevenue = null;
        yesterdayRevenue = null;
        weekRevenue = null;
        lastWeekRevenue = null;
        monthRevenue = null;
        lastMonthRevenue = null;
        return this;
    }
}
