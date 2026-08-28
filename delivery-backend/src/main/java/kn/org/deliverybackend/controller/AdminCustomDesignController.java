package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.customdesign.CustomDesignItemAdminDTO;
import kn.org.deliverybackend.dto.customdesign.CustomDesignLogoAdminDTO;
import kn.org.deliverybackend.service.CustomDesignAdminService;
import kn.org.deliverybackend.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin management of the custom-design studio assets: designable garments
 * (with colours, sizes and print techniques) and the logo library. Secured by
 * Keycloak (SecurityConfig chain 1 owns {@code /admin/**}).
 */
@RestController
@RequestMapping("/admin/custom-design")
@RequiredArgsConstructor
@Tag(name = "Admin: Custom Design")
public class AdminCustomDesignController {

    private final CustomDesignAdminService adminService;
    private final FileStorageService fileStorageService;

    @Value("${minio.custom-design-bucket-name:custom-design-images}")
    private String bucket;

    /** Upload a garment mockup or logo image; returns its public URL for use in an upsert. */
    @PostMapping(value = "/uploads/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadImage(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(Map.of("url", fileStorageService.uploadFile(file, bucket)));
    }

    // ── Garments ────────────────────────────────────────────────────────────

    @GetMapping("/items")
    public ResponseEntity<List<CustomDesignItemAdminDTO>> listItems() {
        return ResponseEntity.ok(adminService.listItems());
    }

    @PostMapping("/items")
    public ResponseEntity<CustomDesignItemAdminDTO> upsertItem(@Valid @RequestBody CustomDesignItemAdminDTO dto) {
        return ResponseEntity.ok(adminService.upsertItem(dto));
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable UUID id) {
        adminService.deleteItem(id);
        return ResponseEntity.noContent().build();
    }

    // ── Logo library ──────────────────────────────────────────────────────────

    @GetMapping("/logos")
    public ResponseEntity<List<CustomDesignLogoAdminDTO>> listLogos() {
        return ResponseEntity.ok(adminService.listLogos());
    }

    @PostMapping("/logos")
    public ResponseEntity<CustomDesignLogoAdminDTO> upsertLogo(@Valid @RequestBody CustomDesignLogoAdminDTO dto) {
        return ResponseEntity.ok(adminService.upsertLogo(dto));
    }

    @DeleteMapping("/logos/{id}")
    public ResponseEntity<Void> deleteLogo(@PathVariable UUID id) {
        adminService.deleteLogo(id);
        return ResponseEntity.noContent().build();
    }
}
