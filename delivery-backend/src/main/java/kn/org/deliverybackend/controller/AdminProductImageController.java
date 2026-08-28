package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.productimage.ProductImageDTO;
import kn.org.deliverybackend.dto.productimage.ProductImageMetadataDTO;
import kn.org.deliverybackend.service.ProductImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/admin/products/{productId}/images")
@RequiredArgsConstructor
@Tag(name = "Admin: Product Images")
public class AdminProductImageController {

    private final ProductImageService imageService;

    @GetMapping
    public ResponseEntity<List<ProductImageDTO>> list(@PathVariable Long productId) {
        return ResponseEntity.ok(imageService.listForProduct(productId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductImageDTO> upload(
            @PathVariable Long productId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "altText", required = false) String altText,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
            @RequestParam(value = "isPrimary", required = false) Boolean isPrimary) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(imageService.upload(productId, file, altText, sortOrder, isPrimary));
    }

    @PutMapping("/{imageId}")
    public ResponseEntity<ProductImageDTO> updateMetadata(
            @PathVariable Long productId,
            @PathVariable Long imageId,
            @Valid @RequestBody ProductImageMetadataDTO metadata) {
        return ResponseEntity.ok(imageService.updateMetadata(productId, imageId, metadata));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> delete(@PathVariable Long productId, @PathVariable Long imageId) {
        imageService.delete(productId, imageId);
        return ResponseEntity.noContent().build();
    }
}
