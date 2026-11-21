package kn.org.deliverybackend.dto.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The other orders from the same phone number as an order (typed any way:
 * "+880 1712-345678" is "01712345678"), to tell a trusted customer from one
 * who has cancelled or refused before. {@code active} counts the ones still
 * on their way. {@code orders} is left empty on the order list's counts.
 */
public record CustomerHistoryDTO(int delivered,
                                 int cancelled,
                                 int returned,
                                 int active,
                                 List<Entry> orders) {

    public static final CustomerHistoryDTO NONE = new CustomerHistoryDTO(0, 0, 0, 0, List.of());

    public int total() {
        return delivered + cancelled + returned + active;
    }

    public record Entry(UUID id,
                        String orderNumber,
                        LocalDateTime createdAt,
                        BigDecimal totalAmount,
                        String orderStatus) {
    }
}
