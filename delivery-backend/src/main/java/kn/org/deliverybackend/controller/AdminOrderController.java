package kn.org.deliverybackend.controller;

import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.OrderDTO;
import kn.org.deliverybackend.dto.OrderSummaryDTO;
import kn.org.deliverybackend.dto.invoice.InvoiceSendResultDTO;
import kn.org.deliverybackend.dto.order.OrderStatusHistoryDTO;
import kn.org.deliverybackend.dto.order.OrderStatusUpdateRequestDTO;
import kn.org.deliverybackend.dto.order.OrderTrackingDTO;
import kn.org.deliverybackend.enumeration.OrderStatus;
import kn.org.deliverybackend.service.InvoiceService;
import kn.org.deliverybackend.service.OrderHistoryService;
import kn.org.deliverybackend.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AdminOrderController {

    private final OrderHistoryService orderHistoryService;
    private final OrderService orderService;
    private final InvoiceService invoiceService;

    @GetMapping("/summary")
    public ResponseEntity<OrderSummaryDTO> getSummary() {
        return ResponseEntity.ok(orderHistoryService.getSummary());
    }

    @GetMapping
    public ResponseEntity<List<OrderDTO>> getAllOrders() {
        return ResponseEntity.ok(orderHistoryService.getAllOrders());
    }

    @GetMapping("/paged")
    public ResponseEntity<Page<OrderDTO>> getOrdersPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String excludeStatus,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) {
        return ResponseEntity.ok(orderHistoryService.getOrdersPaged(page, size, status, excludeStatus, fromDate, toDate));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<OrderDTO>> getOrdersByStatus(@PathVariable String status) {
        return ResponseEntity.ok(orderHistoryService.getOrdersByStatus(status));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDTO> getOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderHistoryService.getOrderByIdForAdmin(orderId));
    }

    /** Invoice PDF for viewing / printing. */
    @GetMapping("/{orderId}/invoice/pdf")
    public ResponseEntity<byte[]> invoicePdf(@PathVariable UUID orderId) {
        byte[] pdf = invoiceService.generateInvoicePdf(orderId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"invoice-" + orderId + ".pdf\"")
                .body(pdf);
    }

    /** Emails the invoice PDF to the customer and sends a short SMS. */
    @PostMapping("/{orderId}/invoice/send")
    public ResponseEntity<InvoiceSendResultDTO> sendInvoice(@PathVariable UUID orderId) {
        return ResponseEntity.ok(invoiceService.sendInvoice(orderId));
    }

    /**
     * State-machine-validated status update. Body shape:
     * <pre>
     *   { "status": "SHIPPED", "note": "...", "courierName": "DHL", "trackingNumber": "..." }
     * </pre>
     * Returns 400 if the requested transition is not allowed from the current
     * status. Persists an audit row and fires an email to the customer.
     */
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderDTO> updateOrderStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderStatusUpdateRequestDTO body,
            @AuthenticationPrincipal Jwt jwt) {
        String changedBy = jwt != null
                ? jwt.getClaimAsString("preferred_username") != null
                        ? "admin:" + jwt.getClaimAsString("preferred_username")
                        : "admin:" + jwt.getSubject()
                : "admin";
        return ResponseEntity.ok(orderHistoryService.updateOrderStatus(orderId, body, changedBy));
    }

    /** Returns the full status-history timeline for an order. */
    @GetMapping("/{orderId}/track")
    public ResponseEntity<OrderTrackingDTO> getTracking(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.getOrderTracking(orderId));
    }

    /** Lists all status values + which transitions are legal from each. */
    @GetMapping("/statuses")
    public ResponseEntity<List<StatusOptionDTO>> getStatusOptions() {
        List<StatusOptionDTO> options = Arrays.stream(OrderStatus.values())
                .filter(s -> s != OrderStatus.PENDING) // hide deprecated alias from UI
                .map(s -> new StatusOptionDTO(
                        s.name(),
                        s.nextStates().stream().map(Enum::name).collect(Collectors.toUnmodifiableSet())))
                .collect(Collectors.toUnmodifiableList());
        return ResponseEntity.ok(options);
    }

    public record StatusOptionDTO(String status, Set<String> allowedNext) {}

    public record HistoryListResponse(List<OrderStatusHistoryDTO> history) {}
}
