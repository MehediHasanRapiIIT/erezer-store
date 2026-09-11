package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
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

    /** {@code q} searches reference, customer name, phone, email and item. */
    @RequiresPermission(Perm.CUSTOM_ORDERS_VIEW)
    @GetMapping
    public ResponseEntity<Page<CustomOrderSummaryDTO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean history,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.listOrders(status, q, page, size, history));
    }

    @RequiresPermission(Perm.CUSTOM_ORDERS_VIEW)
    @GetMapping("/{id}")
    public ResponseEntity<CustomOrderDTO> get(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getOrder(id));
    }

    @RequiresPermission(Perm.CUSTOM_ORDERS_UPDATE)
    @PatchMapping("/{id}")
    public ResponseEntity<CustomOrderDTO> updateStatus(@PathVariable UUID id,
                                                       @Valid @RequestBody CustomOrderStatusUpdateDTO update) {
        return ResponseEntity.ok(adminService.updateOrderStatus(id, update));
    }

    @RequiresPermission(Perm.CUSTOM_ORDERS_DELETE)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adminService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}
