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

    /**
     * The "Show qty" switch in the products list: the product page shows the stock
     * quantity (QUANTITY) or labels (LABEL), or follows its category (CATEGORY).
     */
    @RequiresPermission(Perm.PRODUCTS_EDIT)
    @PatchMapping("/{id}/stock-display")
    public ResponseEntity<ProductResponseDTO> setStockDisplay(
            @PathVariable Long id,
            @RequestParam("value") kn.org.deliverybackend.enumeration.StockDisplay value) {
        ProductResponseDTO result = productService.setStockDisplay(id, value);
        kn.org.deliverybackend.access.StaffAccess.describe(switch (value) {
            case QUANTITY -> "Showed the stock quantity for " + result.getName();
            case LABEL -> "Showed stock labels for " + result.getName();
            case CATEGORY -> "Made " + result.getName() + " follow its category for stock";
        });
        return ResponseEntity.ok(result);
    }
}
