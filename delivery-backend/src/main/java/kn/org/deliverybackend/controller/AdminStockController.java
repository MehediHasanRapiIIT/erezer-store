package kn.org.deliverybackend.controller;

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
    @GetMapping("/inventory/summary")
    public ResponseEntity<InventorySummaryDTO> getSummary() {
        return ResponseEntity.ok(inventoryService.getSummary());
    }

    /** Full inventory list with SKU, unit, threshold */
    @GetMapping("/inventory")
    public ResponseEntity<List<StockResponseDTO>> getAllStock() {
        return ResponseEntity.ok(inventoryService.getAllStockDetails());
    }

    @GetMapping("/products/{id}/stock")
    public ResponseEntity<StockResponseDTO> getStock(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getStockStatus(id));
    }

    @PutMapping("/products/{id}/stock")
    public ResponseEntity<StockResponseDTO> updateStock(
            @PathVariable Long id,
            @Valid @RequestBody AdminStockUpdateRequestDTO request) {
        return ResponseEntity.ok(inventoryService.updateStock(id, request));
    }

    /** Bulk SET stock for multiple products at once */
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
