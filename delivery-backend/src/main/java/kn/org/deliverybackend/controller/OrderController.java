package kn.org.deliverybackend.controller;

import jakarta.servlet.http.HttpServletRequest;
import kn.org.deliverybackend.exception.RateLimitExceededException;
import kn.org.deliverybackend.util.ClientIp;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.OrderDTO;
import kn.org.deliverybackend.dto.order.CancelOrderRequestDTO;
import kn.org.deliverybackend.dto.order.GuestOrderRequestDTO;
import kn.org.deliverybackend.dto.order.OrderTrackingDTO;
import kn.org.deliverybackend.dto.order.UpdateOrderContactRequestDTO;
import kn.org.deliverybackend.dto.request.order.PlaceOrderRequestDTO;
import kn.org.deliverybackend.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/app/consumer")
@RequiredArgsConstructor
public class OrderController {

    private static final int GUEST_ORDERS_PER_WINDOW = 20;
    private static final java.time.Duration GUEST_WINDOW = java.time.Duration.ofMinutes(10);

    private final OrderService orderService;
    private final kn.org.deliverybackend.service.RateLimiterService rateLimiter;

    @PostMapping("/{userId}/orders")
    public ResponseEntity<OrderDTO> placeOrder(
            @PathVariable UUID userId,
            @Valid @RequestBody PlaceOrderRequestDTO request) {
        request.setClientId(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(request));
    }

    /**
     * Guest checkout. No authentication required — see SecurityConfig
     * whitelist for {@code POST /app/consumer/guest/orders}. The order is
     * stored with {@code clientId == null}; the customer's email is snapshot
     * onto the order row so we can email status updates without a Users link.
     */
    @PostMapping("/guest/orders")
    public ResponseEntity<OrderDTO> placeGuestOrder(@Valid @RequestBody GuestOrderRequestDTO request,
                                                    HttpServletRequest http) {
        // No account to hold accountable, so cap orders per sender. Loose enough for
        // many real shoppers behind one mobile-network address, tight enough to
        // stop a script filling the order list with fakes.
        if (!rateLimiter.tryAcquire("rl:guest-order:" + ClientIp.of(http), GUEST_ORDERS_PER_WINDOW, GUEST_WINDOW)) {
            throw new RateLimitExceededException((int) GUEST_WINDOW.toSeconds(), GUEST_ORDERS_PER_WINDOW,
                    (int) GUEST_WINDOW.toSeconds());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeGuestOrder(request));
    }

    @PostMapping("/{userId}/orders/{orderId}/cancel")
    public ResponseEntity<OrderDTO> cancelOrder(
            @PathVariable UUID userId,
            @PathVariable UUID orderId,
            @Valid @RequestBody(required = false) CancelOrderRequestDTO request) {
        return ResponseEntity.ok(orderService.cancelOrder(userId, orderId, request));
    }

    @PatchMapping("/{userId}/orders/{orderId}/contact")
    public ResponseEntity<OrderDTO> updateContact(
            @PathVariable UUID userId,
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderContactRequestDTO request) {
        return ResponseEntity.ok(orderService.updateOrderContact(userId, orderId, request));
    }

    @GetMapping("/{userId}/orders/{orderId}/track")
    public ResponseEntity<OrderTrackingDTO> trackOrder(
            @PathVariable UUID userId,
            @PathVariable UUID orderId) {
        // Ownership-checked: returns 404 if the order isn't owned by this user.
        return ResponseEntity.ok(orderService.getOrderTracking(userId, orderId));
    }
}
