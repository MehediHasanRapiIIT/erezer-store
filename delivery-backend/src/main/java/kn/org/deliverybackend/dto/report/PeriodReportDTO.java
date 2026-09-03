package kn.org.deliverybackend.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One complete business report for a day, week, month, calendar year or
 * fiscal year, with the previous period alongside for comparison.
 *
 * <p>Every money figure is in the shop currency (BDT) and every count is a
 * whole order or unit. Definitions, so the numbers can be audited:
 * <ul>
 *   <li><b>Placed orders</b> — every non-deleted order created inside the
 *       period (business-local time), whatever happened to it later.</li>
 *   <li><b>Orders</b> (counted) — placed orders that are not CANCELLED or
 *       RETURNED. Everything called "revenue" or "sales" sums only these.</li>
 *   <li><b>Gross sales</b> — merchandise value before discounts
 *       ({@code subtotal_amount}).</li>
 *   <li><b>Net revenue</b> — what customers pay: gross − discounts + shipping
 *       + custom-size surcharges + VAT ({@code total_amount}).</li>
 *   <li><b>Delivered revenue</b> — net revenue of orders already DELIVERED;
 *       for cash-on-delivery this is the money actually in hand.</li>
 *   <li><b>Average order value</b> — net revenue ÷ counted orders.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodReportDTO {

    private String type;              // DAY | WEEK | MONTH | YEAR | FISCAL_YEAR
    private String label;             // "Thu, 4 Sep 2026", "FY 2026-27" …
    private LocalDate start;          // first day, inclusive
    private LocalDate end;            // last day, inclusive
    private String zone;              // e.g. Asia/Dhaka
    private String weekStart;         // e.g. SUNDAY
    private String currency;          // BDT
    private LocalDateTime generatedAt; // business-local time the report was built
    private boolean complete;         // false while the period is still running

    private PeriodMetrics current;
    private PeriodMetrics previous;
    private String previousLabel;
    private LocalDate previousStart;
    private LocalDate previousEnd;

    private String bucketUnit;        // hour | day | month
    private List<Bucket> breakdown;   // one entry per bucket, zero-filled

    private List<StatusCount> byStatus;
    private List<PaymentSplit> byPayment;
    private List<TopProductDTO> topProducts;
    private List<TopCategoryDTO> topCategories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodMetrics {
        // Order counts (attributed by order date)
        private long placedOrders;
        private long orders;
        private long deliveredOrders;
        private long cancelledOrders;
        private long returnedOrders;
        private long pendingOrders;      // PLACED, awaiting acceptance
        private long inProgressOrders;   // accepted … out for delivery
        private long unitsSold;

        // Money (counted orders only)
        private BigDecimal grossSales;
        private BigDecimal discounts;
        private BigDecimal shipping;
        private BigDecimal surcharges;
        private BigDecimal vat;
        private BigDecimal netRevenue;
        private BigDecimal deliveredRevenue;
        private BigDecimal averageOrderValue;

        // Lost value
        private BigDecimal cancelledValue;
        private BigDecimal returnedValue;

        // Customers
        private long uniqueCustomers;
        private long newCustomers;

        // Rates, in percent with one decimal
        private double cancellationRate; // cancelled ÷ placed
        private double returnRate;       // returned ÷ placed
        private double deliveryRate;     // delivered ÷ placed

        // Fulfilment events inside the period (attributed by event date)
        private long deliveredInPeriodOrders;
        private BigDecimal deliveredInPeriodRevenue;
        private long cancelledInPeriodOrders;
        private BigDecimal cancelledInPeriodValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Bucket {
        private LocalDateTime bucketStart; // business-local
        private String label;              // "09:00", "Sun 6", "Sep 2026"
        private long placedOrders;
        private long orders;
        private long cancelledOrders;
        private BigDecimal netRevenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusCount {
        private String status;
        private long count;
        private BigDecimal value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSplit {
        private String method;            // CASH | BKASH | CARD | …
        private long orders;              // counted orders
        private BigDecimal revenue;       // net revenue of counted orders
        private long deliveredOrders;
        private BigDecimal deliveredRevenue;
        private long undeliveredOrders;   // counted but not yet delivered
        private BigDecimal undeliveredValue; // for CASH: COD still to collect
        private long cancelledOrders;
    }
}
