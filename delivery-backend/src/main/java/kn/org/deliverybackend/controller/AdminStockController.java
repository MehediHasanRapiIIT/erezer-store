package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.request.product.AdminStockUpdateRequestDTO;
import kn.org.deliverybackend.dto.request.product.BulkStockUpdateRequestDTO;
import kn.org.deliverybackend.dto.response.product.InventorySummaryDTO;
import kn.org.deliverybackend.dto.response.product.StockResponseDTO;
import kn.org.deliverybackend.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    /** One page of products low on stock or out of stock, for the restock alerts; {@code q} searches name and SKU. */
    @RequiresPermission(Perm.INVENTORY_VIEW)
    @GetMapping("/inventory/alerts")
    public ResponseEntity<org.springframework.data.domain.Page<StockResponseDTO>> lowStock(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(inventoryService.lowStock(q, page, size));
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

    /** An exact stock figure for each product chosen on the page. */
    @RequiresPermission(Perm.INVENTORY_EDIT)
    @PutMapping("/inventory/bulk")
    public ResponseEntity<List<StockResponseDTO>> bulkUpdateStock(
            @Valid @RequestBody BulkStockUpdateRequestDTO request) {
        List<StockResponseDTO> results = inventoryService.setStockForEach(request);
        kn.org.deliverybackend.access.StaffAccess.describe(
                "Set stock for " + results.size() + " product" + (results.size() == 1 ? "" : "s"));
        return ResponseEntity.ok(results);
    }

    /**
     * One change for many products at once: add to, remove from or set the stock
     * of the chosen products, a whole category, or every product in the shop.
     */
    @RequiresPermission(Perm.INVENTORY_EDIT)
    @PutMapping("/inventory/bulk-adjust")
    public ResponseEntity<kn.org.deliverybackend.dto.response.product.BulkStockResultDTO> adjustStock(
            @Valid @RequestBody kn.org.deliverybackend.dto.request.product.BulkStockAdjustRequestDTO request) {
        var result = inventoryService.adjustStock(request);
        kn.org.deliverybackend.access.StaffAccess.describe(result.getMessage());
        return ResponseEntity.ok(result);
    }
}
