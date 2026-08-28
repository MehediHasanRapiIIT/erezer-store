package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.dto.settings.StoreSettingsDTO;
import kn.org.deliverybackend.service.StoreSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, read-only access to store settings for the storefront (return policy,
 * support contact, size chart). Anonymous — see SecurityConfig chain 2.
 */
@RestController
@RequestMapping("/api/store-settings")
@RequiredArgsConstructor
@Tag(name = "Store Settings")
public class StoreSettingsController {

    private final StoreSettingsService storeSettingsService;

    @GetMapping
    public ResponseEntity<StoreSettingsDTO> get() {
        return ResponseEntity.ok(storeSettingsService.get());
    }
}
