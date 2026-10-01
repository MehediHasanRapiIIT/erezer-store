package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.StaffAccess;
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

    /**
     * Several sizes at once, e.g. S, M and L with stock for each. All of them are
     * added or none are: a size the product already has, or one listed twice,
     * stops the lot.
     */
    @RequiresPermission(Perm.PRODUCTS_VARIANTS)
    @PostMapping("/bulk")
    public ResponseEntity<List<VariantResponseDTO>> createSeveral(
            @PathVariable Long productId,
            @RequestBody @jakarta.validation.constraints.NotEmpty(message = "Choose at least one size")
            @jakarta.validation.constraints.Size(max = 20, message = "Up to 20 sizes at a time")
            List<@Valid VariantRequestDTO> requests) {
        requests.forEach(r -> checkGuardedFields(r, null));
        List<VariantResponseDTO> created = variantService.createAll(productId, requests);
        StaffAccess.describe("Added " + created.size() + (created.size() == 1 ? " size" : " sizes")
                + " to product " + productId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
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

    /** See {@link ProductAccess#checkVariantFields}. */
    private static void checkGuardedFields(VariantRequestDTO request, VariantResponseDTO current) {
        ProductAccess.checkVariantFields(request, current);
    }
}
