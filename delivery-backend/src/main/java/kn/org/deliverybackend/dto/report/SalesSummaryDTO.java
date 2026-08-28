package kn.org.deliverybackend.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesSummaryDTO {
    private long totalOrders;
    private long deliveredOrders;
    private long cancelledOrders;
    private long returnedOrders;
    private BigDecimal grossRevenue;     // sum of total_amount across all orders
    private BigDecimal netRevenue;        // gross excluding cancelled/returned
    private BigDecimal averageOrderValue; // net / delivered count
    private long uniqueCustomers;
}
