package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.customdesign.CustomOrderDTO;
import kn.org.deliverybackend.dto.customdesign.CustomOrderStatusUpdateDTO;
import kn.org.deliverybackend.dto.customdesign.CustomOrderSummaryDTO;
import kn.org.deliverybackend.service.CustomDesignAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin inbox for submitted custom-design quote requests. Secured by Keycloak
 * (SecurityConfig chain 1 owns {@code /admin/**}).
 */
@RestController
@RequestMapping("/admin/custom-orders")
@RequiredArgsConstructor
@Tag(name = "Admin: Custom Orders")
public class AdminCustomOrderController {

    private final CustomDesignAdminService adminService;

    @GetMapping
    public ResponseEntity<Page<CustomOrderSummaryDTO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean history,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.listOrders(status, page, size, history));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomOrderDTO> get(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getOrder(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CustomOrderDTO> updateStatus(@PathVariable UUID id,
                                                       @Valid @RequestBody CustomOrderStatusUpdateDTO update) {
        return ResponseEntity.ok(adminService.updateOrderStatus(id, update));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adminService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}
