package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.dto.response.product.ProductResponseDTO;
import kn.org.deliverybackend.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only product endpoints that don't fit the public catalogue API: the
 * searchable product list and quick flag toggles. Keycloak-protected (chain 1).
 */
@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AdminProductController {

    private final ProductService productService;

    /**
     * The Products page: {@code q} searches name, SKU, brand and category name
     * across all products, {@code categoryId} keeps one category, newest first.
     */
    @RequiresPermission(Perm.PRODUCTS_VIEW)
    @GetMapping
    public Page<ProductResponseDTO> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return productService.adminSearch(q, categoryId, page, size);
    }

    /** Toggle the "Featured products" home-page flag without touching pricing/stock. */
    @RequiresPermission(Perm.PRODUCTS_FEATURE)
    @PatchMapping("/{id}/featured")
    public ResponseEntity<ProductResponseDTO> setFeatured(
            @PathVariable Long id,
            @RequestParam("value") boolean value) {
        return ResponseEntity.ok(productService.setFeatured(id, value));
    }
}
