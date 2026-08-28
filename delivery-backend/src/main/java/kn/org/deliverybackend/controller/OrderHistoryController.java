package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.dto.OrderDTO;
import kn.org.deliverybackend.service.OrderHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/app/consumer")
@RequiredArgsConstructor
public class OrderHistoryController {

    private final OrderHistoryService orderHistoryService;

    // C-25: View order history
    @GetMapping("/{userId}/orders")
    public ResponseEntity<List<OrderDTO>> getOrderHistory(@PathVariable UUID userId) {
        return ResponseEntity.ok(orderHistoryService.getOrderHistory(userId));
    }

    // C-26: View order details
    @GetMapping("/{userId}/orders/{orderId}")
    public ResponseEntity<OrderDTO> getOrderDetails(
            @PathVariable UUID userId,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(orderHistoryService.getOrderDetails(userId, orderId));
    }
}
