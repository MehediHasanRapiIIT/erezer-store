package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.OrderDTO;
import kn.org.deliverybackend.dto.order.CancelOrderRequestDTO;
import kn.org.deliverybackend.dto.order.GuestOrderRequestDTO;
import kn.org.deliverybackend.dto.order.OrderTrackingDTO;
import kn.org.deliverybackend.dto.order.UpdateOrderContactRequestDTO;
import kn.org.deliverybackend.dto.request.order.PlaceOrderRequestDTO;

import java.util.UUID;

public interface OrderService {

    OrderDTO placeOrder(PlaceOrderRequestDTO request);

    OrderDTO placeGuestOrder(GuestOrderRequestDTO request);

    OrderDTO cancelOrder(UUID userId, UUID orderId, CancelOrderRequestDTO request);

    /** Customer updates shipping address + phone while the order is still PLACED. */
    OrderDTO updateOrderContact(UUID userId, UUID orderId, UpdateOrderContactRequestDTO request);

    OrderTrackingDTO getOrderTracking(UUID orderId);

    /** {@link #getOrderTracking(UUID)} as a customer may see it: without the shop's own call check. */
    OrderTrackingDTO getOrderTrackingForCustomer(UUID orderId);

    /** Ownership-checked variant for customer-facing tracking (404s if not the owner). */
    OrderTrackingDTO getOrderTracking(UUID userId, UUID orderId);
}
