package kn.org.deliverybackend.dto.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTrackingDTO {
    private UUID orderId;
    private String currentStatus;
    private String courierName;
    private String trackingNumber;
    private List<OrderStatusHistoryDTO> history;
    /** Statuses that the customer is allowed to request next (e.g. cancellation). */
    private List<String> allowedCustomerNextStates;
    /** ISO timestamp at which customer self-cancel becomes impossible. Null if already past or N/A. */
    private String cancellationDeadline;
}
