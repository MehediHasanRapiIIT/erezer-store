package kn.org.deliverybackend.controller;

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

    @GetMapping
    public ResponseEntity<List<VariantResponseDTO>> list(@PathVariable Long productId) {
        return ResponseEntity.ok(variantService.listForProduct(productId));
    }

    @PostMapping
    public ResponseEntity<VariantResponseDTO> create(
            @PathVariable Long productId,
            @Valid @RequestBody VariantRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(variantService.create(productId, request));
    }

    @PutMapping("/{variantId}")
    public ResponseEntity<VariantResponseDTO> update(
            @PathVariable Long productId,
            @PathVariable Long variantId,
            @Valid @RequestBody VariantRequestDTO request) {
        return ResponseEntity.ok(variantService.update(productId, variantId, request));
    }

    @DeleteMapping("/{variantId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long productId,
            @PathVariable Long variantId) {
        variantService.delete(productId, variantId);
        return ResponseEntity.noContent().build();
    }
}
