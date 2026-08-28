package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.dto.settings.StoreSettingsDTO;
import kn.org.deliverybackend.service.StoreSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin read/update of the singleton store settings. Secured by Keycloak
 * (SecurityConfig chain 1 owns {@code /admin/**}).
 */
@RestController
@RequestMapping("/admin/store-settings")
@RequiredArgsConstructor
@Tag(name = "Admin: Store Settings")
public class AdminStoreSettingsController {

    private final StoreSettingsService storeSettingsService;

    @GetMapping
    public ResponseEntity<StoreSettingsDTO> get() {
        return ResponseEntity.ok(storeSettingsService.get());
    }

    @PutMapping
    public ResponseEntity<StoreSettingsDTO> update(@RequestBody StoreSettingsDTO request) {
        return ResponseEntity.ok(storeSettingsService.update(request));
    }
}
