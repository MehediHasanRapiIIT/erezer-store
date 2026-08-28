package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.dto.response.product.ProductResponseDTO;
import kn.org.deliverybackend.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only quick actions on products that don't fit the full multipart update
 * (e.g. flag toggles from the product list). Keycloak-protected (chain 1).
 */
@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AdminProductController {

    private final ProductService productService;

    /** Toggle the "Featured products" home-page flag without touching pricing/stock. */
    @PatchMapping("/{id}/featured")
    public ResponseEntity<ProductResponseDTO> setFeatured(
            @PathVariable Long id,
            @RequestParam("value") boolean value) {
        return ResponseEntity.ok(productService.setFeatured(id, value));
    }
}
