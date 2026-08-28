package kn.org.deliverybackend.controller;

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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponseDTO> createProduct(
            @Valid @RequestPart("productRequestDTO") ProductRequestDTO productRequestDTO,
            @RequestPart(value = "image", required = false) MultipartFile image) {

        return ResponseEntity.ok(productService.createProduct(productRequestDTO, image));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductResponseDTO> updateProduct(
            @PathVariable Long id,
            @Valid ProductRequestDTO productRequestDTO,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.ok(productService.updateProduct(id, productRequestDTO, image));
    }

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
