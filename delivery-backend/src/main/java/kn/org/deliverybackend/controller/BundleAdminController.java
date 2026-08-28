package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.bundle.BundleOfferRequestDTO;
import kn.org.deliverybackend.dto.bundle.BundleOfferResponseDTO;
import kn.org.deliverybackend.service.BundleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Admin CRUD for bundle offers. Secured by Keycloak (SecurityConfig chain 1 owns /admin/**). */
@RestController
@RequestMapping("/admin/bundles")
@RequiredArgsConstructor
@Tag(name = "Admin: Bundles")
public class BundleAdminController {

    private final BundleService bundleService;

    @GetMapping
    public ResponseEntity<List<BundleOfferResponseDTO>> list() {
        return ResponseEntity.ok(bundleService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BundleOfferResponseDTO> get(@PathVariable UUID id) {
        return ResponseEntity.ok(bundleService.get(id));
    }

    @PostMapping
    public ResponseEntity<BundleOfferResponseDTO> create(@Valid @RequestBody BundleOfferRequestDTO request) {
        return ResponseEntity.ok(bundleService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BundleOfferResponseDTO> update(@PathVariable UUID id,
                                                         @Valid @RequestBody BundleOfferRequestDTO request) {
        return ResponseEntity.ok(bundleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        bundleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
