package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.service.ProductPricing;
import java.math.BigDecimal;
import kn.org.deliverybackend.access.RequiresPermission;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.productimage.ProductImageDTO;
import kn.org.deliverybackend.dto.request.product.ProductRequestDTO;
import kn.org.deliverybackend.dto.response.product.ProductResponseDTO;
import kn.org.deliverybackend.dto.response.product.StockResponseDTO;
import kn.org.deliverybackend.dto.variant.VariantResponseDTO;
import kn.org.deliverybackend.service.InventoryService;
import kn.org.deliverybackend.service.ProductImageService;
import kn.org.deliverybackend.service.ProductService;
import kn.org.deliverybackend.service.VariantService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@CrossOrigin("*")
public class ProductController {

    private final ProductService productService;
    private final InventoryService inventoryService;
    private final VariantService variantService;
    private final ProductImageService productImageService;

    @GetMapping
    public ResponseEntity<List<ProductResponseDTO>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    @GetMapping("/paged")
    public ResponseEntity<Page<ProductResponseDTO>> getProductsPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(productService.getProductsPaged(page, size));
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProductResponseDTO>> searchProducts(@RequestParam String name) {
        return ResponseEntity.ok(productService.searchProducts(name));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @RequiresPermission({Perm.PRODUCTS_CREATE, Perm.PRODUCTS_PRICE})
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponseDTO> createProduct(
            @Valid @RequestPart("productRequestDTO") ProductRequestDTO productRequestDTO,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        checkGuardedFields(productRequestDTO, null);
        return ResponseEntity.ok(productService.createProduct(productRequestDTO, image));
    }

    @RequiresPermission(Perm.PRODUCTS_EDIT)
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponseDTO> updateProduct(
            @PathVariable Long id,
            @Valid ProductRequestDTO productRequestDTO,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        checkGuardedFields(productRequestDTO, productService.getProductById(id));
        return ResponseEntity.ok(productService.updateProduct(id, productRequestDTO, image));
    }

    /**
     * Parts of the product form that need a permission of their own: what
     * customers pay (price and sale discount), the home-page flags, and
     * "Never discount". {@code current} is null when adding; the price of a
     * new product is covered by the endpoint's own rule.
     */
    private static void checkGuardedFields(ProductRequestDTO request, ProductResponseDTO current) {
        if (current != null) {
            BigDecimal salePriceBefore = current.getDiscountPrice() != null ? current.getDiscountPrice() : current.getPrice();
            BigDecimal salePriceAfter = ProductPricing.salePrice(request.getPrice(), request.getDiscountPercentage());
            if (!ProductPricing.sameAmount(request.getPrice(), current.getPrice())
                    || !ProductPricing.sameAmount(salePriceAfter, salePriceBefore)) {
                StaffAccess.require(Perm.PRODUCTS_PRICE);
            }
        }
        if (flagChanged(request.getIsFeatured(), current == null ? null : current.getIsFeatured())
                || flagChanged(request.getIsNewArrival(), current == null ? null : current.getIsNewArrival())) {
            StaffAccess.require(Perm.PRODUCTS_FEATURE);
        }
        if (flagChanged(request.getDiscountExcluded(), current == null ? null : current.getDiscountExcluded())) {
            StaffAccess.require(Perm.DISCOUNTS_SWITCHES);
        }
    }

    /** True when a flag is sent and differs from what is stored. A flag not sent changes nothing; missing counts as off. */
    private static boolean flagChanged(Boolean requested, Boolean before) {
        return requested != null && requested != Boolean.TRUE.equals(before);
    }

    @RequiresPermission(Perm.PRODUCTS_DELETE)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    // Polling endpoint — no auth required for read
    @GetMapping("/{id}/stock-status")
    public ResponseEntity<StockResponseDTO> getStockStatus(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getStockStatus(id));
    }

    // ── Phase 3 public endpoints ───────────────────────────────────────────────

    @GetMapping("/{id}/variants")
    public ResponseEntity<List<VariantResponseDTO>> getVariants(@PathVariable Long id) {
        return ResponseEntity.ok(variantService.listForProduct(id));
    }

    @GetMapping("/{id}/images")
    public ResponseEntity<List<ProductImageDTO>> getImages(@PathVariable Long id) {
        return ResponseEntity.ok(productImageService.listForProduct(id));
    }

    @GetMapping("/{id}/related")
    public ResponseEntity<List<ProductResponseDTO>> getRelated(
            @PathVariable Long id,
            @RequestParam(defaultValue = "8") int limit) {
        return ResponseEntity.ok(productService.getRelatedProducts(id, limit));
    }
}
