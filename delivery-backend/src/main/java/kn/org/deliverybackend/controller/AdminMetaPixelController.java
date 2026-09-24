package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.dto.auth.MessageResponseDTO;
import kn.org.deliverybackend.dto.settings.MetaPixelChangeDTO;
import kn.org.deliverybackend.dto.settings.MetaPixelSettingsDTO;
import kn.org.deliverybackend.service.MetaPixelSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Meta (Facebook) Pixel settings. The access token is write-only: it goes in
 * from here and is never sent back, not even to the person who typed it.
 */
@RestController
@RequestMapping("/admin/meta-pixel")
@RequiredArgsConstructor
@Tag(name = "Admin: Meta Pixel")
public class AdminMetaPixelController {

    private final MetaPixelSettingsService metaSettings;

    @RequiresPermission(Perm.SETTINGS_META)
    @GetMapping
    public ResponseEntity<MetaPixelSettingsDTO> get() {
        return ResponseEntity.ok(metaSettings.get());
    }

    @RequiresPermission(Perm.SETTINGS_META)
    @PutMapping
    public ResponseEntity<MetaPixelSettingsDTO> update(@Valid @RequestBody MetaPixelChangeDTO change) {
        MetaPixelSettingsDTO saved = metaSettings.update(change);
        // The log says what changed, never the token itself.
        StringBuilder what = new StringBuilder("Meta Pixel: ");
        what.append(saved.isEnabled() ? "on" : "off");
        if (saved.getPixelId() != null) what.append(", ID ").append(saved.getPixelId());
        if (Boolean.TRUE.equals(change.getRemoveToken())) {
            what.append(", access token removed");
        } else if (change.getAccessToken() != null && !change.getAccessToken().isBlank()) {
            what.append(", new access token saved");
        }
        StaffAccess.describe(what.toString());
        return ResponseEntity.ok(saved);
    }

    /** Sends one event to Meta so the owner can check the settings really work. */
    @RequiresPermission(Perm.SETTINGS_META)
    @PostMapping("/test-event")
    public ResponseEntity<MessageResponseDTO> testEvent() {
        String message = metaSettings.sendTestEvent();
        StaffAccess.describe("Sent a Meta Pixel test event");
        return ResponseEntity.ok(MessageResponseDTO.of(message));
    }
}
