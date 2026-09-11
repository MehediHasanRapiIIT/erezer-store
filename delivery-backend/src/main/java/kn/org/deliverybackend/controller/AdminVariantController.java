package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.service.ProductPricing;
import kn.org.deliverybackend.access.RequiresPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.variant.VariantRequestDTO;
import kn.org.deliverybackend.dto.variant.VariantResponseDTO;
import kn.org.deliverybackend.service.VariantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/products/{productId}/variants")
@RequiredArgsConstructor
@Tag(name = "Admin: Product Variants")
public class AdminVariantController {

    private final VariantService variantService;

    @RequiresPermission(value = {Perm.PRODUCTS_VIEW, Perm.PRODUCTS_EDIT, Perm.PRODUCTS_VARIANTS, Perm.INVENTORY_VIEW}, mode = RequiresPermission.Mode.ANY)
    @GetMapping
    public ResponseEntity<List<VariantResponseDTO>> list(@PathVariable Long productId) {
        return ResponseEntity.ok(variantService.listForProduct(productId));
    }

    @RequiresPermission(Perm.PRODUCTS_VARIANTS)
    @PostMapping
    public ResponseEntity<VariantResponseDTO> create(
            @PathVariable Long productId,
            @Valid @RequestBody VariantRequestDTO request) {
        checkGuardedFields(request, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(variantService.create(productId, request));
    }

    @RequiresPermission(Perm.PRODUCTS_VARIANTS)
    @PutMapping("/{variantId}")
    public ResponseEntity<VariantResponseDTO> update(
            @PathVariable Long productId,
            @PathVariable Long variantId,
            @Valid @RequestBody VariantRequestDTO request) {
        variantService.listForProduct(productId).stream()
                .filter(v -> variantId.equals(v.getId()))
                .findFirst()
                .ifPresent(current -> checkGuardedFields(request, current));
        return ResponseEntity.ok(variantService.update(productId, variantId, request));
    }

    @RequiresPermission(Perm.PRODUCTS_VARIANTS)
    @DeleteMapping("/{variantId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long productId,
            @PathVariable Long variantId) {
        variantService.delete(productId, variantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * A size's own price is a price, and its stock is inventory, so changing
     * either needs that permission too. {@code current} is null when adding.
     */
    private static void checkGuardedFields(VariantRequestDTO request, VariantResponseDTO current) {
        if (!ProductPricing.sameAmount(request.getPriceOverride(), current == null ? null : current.getPriceOverride())) {
            StaffAccess.require(Perm.PRODUCTS_PRICE);
        }
        int stockBefore = current == null || current.getStockQuantity() == null ? 0 : current.getStockQuantity();
        if (request.getStockQuantity() != null && request.getStockQuantity() != stockBefore) {
            StaffAccess.require(Perm.INVENTORY_EDIT);
        }
    }
}
