package kn.org.deliverybackend.dto.order;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The orders of some shop days (Dhaka time; both ends included, null = no limit),
 * every status, for the top of the admin order list. {@code active} are the ones
 * still on their way. {@code amount} adds up the orders that weren't cancelled; it
 * is null for staff without "See money totals".
 */
public record OrderDaySummaryDTO(LocalDate fromDate,
                                 LocalDate toDate,
                                 long orders,
                                 long delivered,
                                 long cancelled,
                                 long returned,
                                 long active,
                                 BigDecimal amount) {

    public OrderDaySummaryDTO withoutAmount() {
        return new OrderDaySummaryDTO(fromDate, toDate, orders, delivered, cancelled, returned, active, null);
    }
}
