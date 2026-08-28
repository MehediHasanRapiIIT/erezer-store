package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.dto.bundle.BundleOfferResponseDTO;
import kn.org.deliverybackend.service.BundleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Public storefront bundle-offer endpoints. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Bundles (public)")
public class BundleController {

    private final BundleService bundleService;

    /** Featured bundle for the landing widget; 204 when none. */
    @GetMapping("/api/bundle")
    public ResponseEntity<BundleOfferResponseDTO> featured() {
        BundleOfferResponseDTO dto = bundleService.getFeatured();
        return dto == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(dto);
    }

    @GetMapping("/api/bundles")
    public ResponseEntity<List<BundleOfferResponseDTO>> list() {
        return ResponseEntity.ok(bundleService.listActive());
    }

    @GetMapping("/api/bundles/{id}")
    public ResponseEntity<BundleOfferResponseDTO> byId(@PathVariable UUID id) {
        return ResponseEntity.ok(bundleService.getActive(id));
    }
}
