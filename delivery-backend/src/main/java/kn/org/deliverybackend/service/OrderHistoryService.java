package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.OrderDTO;
import kn.org.deliverybackend.dto.OrderSummaryDTO;
import kn.org.deliverybackend.dto.order.OrderStatusUpdateRequestDTO;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface OrderHistoryService {

    List<OrderDTO> getOrderHistory(UUID userId);
    OrderDTO getOrderDetails(UUID userId, UUID orderId);
    List<OrderDTO> getAllOrders();

    /** Admin: fetch one order (with enriched customer/items) by id. */
    OrderDTO getOrderByIdForAdmin(UUID orderId);
    Page<OrderDTO> getOrdersPaged(int page, int size, String status, String excludeStatus, String fromDate, String toDate);
    List<OrderDTO> getOrdersByStatus(String status);

    /**
     * Admin-driven status update with full audit (state-machine validated, history row,
     * customer email notification). Use this for /admin/orders/{id}/status.
     */
    OrderDTO updateOrderStatus(UUID orderId, OrderStatusUpdateRequestDTO request, String changedBy);

    /** @deprecated kept for any legacy callers; prefer the richer overload. */
    @Deprecated
    OrderDTO updateOrderStatus(UUID orderId, String status);

    OrderSummaryDTO getSummary();
}
