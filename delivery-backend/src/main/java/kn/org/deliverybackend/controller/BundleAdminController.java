package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.bundle.BundleOfferRequestDTO;
import kn.org.deliverybackend.dto.bundle.BundleOfferResponseDTO;
import kn.org.deliverybackend.service.BundleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Admin CRUD for bundle offers. Secured by Keycloak (SecurityConfig chain 1 owns /admin/**). */
@RestController
@RequestMapping("/admin/bundles")
@RequiredArgsConstructor
@Tag(name = "Admin: Bundles")
public class BundleAdminController {

    private final BundleService bundleService;

    @RequiresPermission(Perm.BUNDLES_VIEW)
    @GetMapping
    public ResponseEntity<Page<BundleOfferResponseDTO>> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(bundleService.list(q, page, size));
    }

    @RequiresPermission(Perm.BUNDLES_VIEW)
    @GetMapping("/{id}")
    public ResponseEntity<BundleOfferResponseDTO> get(@PathVariable UUID id) {
        return ResponseEntity.ok(bundleService.get(id));
    }

    @RequiresPermission(Perm.BUNDLES_CREATE)
    @PostMapping
    public ResponseEntity<BundleOfferResponseDTO> create(@Valid @RequestBody BundleOfferRequestDTO request) {
        return ResponseEntity.ok(bundleService.create(request));
    }

    @RequiresPermission(Perm.BUNDLES_EDIT)
    @PutMapping("/{id}")
    public ResponseEntity<BundleOfferResponseDTO> update(@PathVariable UUID id,
                                                         @Valid @RequestBody BundleOfferRequestDTO request) {
        return ResponseEntity.ok(bundleService.update(id, request));
    }

    @RequiresPermission(Perm.BUNDLES_DELETE)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        bundleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
