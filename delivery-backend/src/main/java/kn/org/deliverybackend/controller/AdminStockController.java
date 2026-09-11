package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.request.product.AdminStockUpdateRequestDTO;
import kn.org.deliverybackend.dto.request.product.BulkStockUpdateRequestDTO;
import kn.org.deliverybackend.dto.response.product.InventorySummaryDTO;
import kn.org.deliverybackend.dto.response.product.StockResponseDTO;
import kn.org.deliverybackend.enumeration.StockOperation;
import kn.org.deliverybackend.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AdminStockController {

    private final InventoryService inventoryService;

    /** Summary counts for the 3 inventory cards */
    @RequiresPermission(Perm.INVENTORY_VIEW)
    @GetMapping("/inventory/summary")
    public ResponseEntity<InventorySummaryDTO> getSummary() {
        return ResponseEntity.ok(inventoryService.getSummary());
    }

    /** One page of stock rows with SKU, unit and threshold; {@code q} searches product name and SKU. */
    @RequiresPermission(Perm.INVENTORY_VIEW)
    @GetMapping("/inventory")
    public ResponseEntity<org.springframework.data.domain.Page<StockResponseDTO>> getStockPage(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(inventoryService.stockPage(q, page, size));
    }

    /** Every product low on stock or out of stock, for the restock alerts. */
    @RequiresPermission(Perm.INVENTORY_VIEW)
    @GetMapping("/inventory/alerts")
    public ResponseEntity<List<StockResponseDTO>> lowStock() {
        return ResponseEntity.ok(inventoryService.lowStock());
    }

    @RequiresPermission(Perm.INVENTORY_VIEW)
    @GetMapping("/products/{id}/stock")
    public ResponseEntity<StockResponseDTO> getStock(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getStockStatus(id));
    }

    @RequiresPermission(Perm.INVENTORY_EDIT)
    @PutMapping("/products/{id}/stock")
    public ResponseEntity<StockResponseDTO> updateStock(
            @PathVariable Long id,
            @Valid @RequestBody AdminStockUpdateRequestDTO request) {
        return ResponseEntity.ok(inventoryService.updateStock(id, request));
    }

    /** Bulk SET stock for multiple products at once */
    @RequiresPermission(Perm.INVENTORY_EDIT)
    @PutMapping("/inventory/bulk")
    public ResponseEntity<List<StockResponseDTO>> bulkUpdateStock(
            @Valid @RequestBody BulkStockUpdateRequestDTO request) {
        List<StockResponseDTO> results = request.getUpdates().stream()
                .map(item -> {
                    AdminStockUpdateRequestDTO req = new AdminStockUpdateRequestDTO();
                    req.setOperation(StockOperation.SET);
                    req.setQuantity(item.getQuantity());
                    req.setUnit(item.getUnit());
                    req.setLowStockThreshold(item.getLowStockThreshold());
                    return inventoryService.updateStock(item.getProductId(), req);
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(results);
    }
}
