package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.dto.settings.HomeSectionDTO;
import kn.org.deliverybackend.service.StoreSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The admin "Home page layout" page: which sections of the shop's home page are
 * shown, and in what order. The shop reads the result with its other settings
 * ({@code GET /api/store-settings}).
 */
@RestController
@RequestMapping("/admin/home-layout")
@RequiredArgsConstructor
@Tag(name = "Admin: Home page layout")
public class AdminHomeLayoutController {

    private final StoreSettingsService storeSettingsService;

    @RequiresPermission(value = {Perm.SETTINGS_VIEW, Perm.SETTINGS_HOMEPAGE}, mode = RequiresPermission.Mode.ANY)
    @GetMapping
    public ResponseEntity<List<HomeSectionDTO>> get() {
        return ResponseEntity.ok(storeSettingsService.getHomeLayout());
    }

    /** Takes every section, top to bottom. A section left out goes to the end, switched on. */
    @RequiresPermission(Perm.SETTINGS_HOMEPAGE)
    @PutMapping
    public ResponseEntity<List<HomeSectionDTO>> update(@RequestBody List<HomeSectionDTO> layout) {
        List<HomeSectionDTO> saved = storeSettingsService.updateHomeLayout(layout);
        long shown = saved.stream().filter(HomeSectionDTO::isEnabled).count();
        StaffAccess.describe("Changed the home page layout: " + shown + " of " + saved.size() + " sections shown");
        return ResponseEntity.ok(saved);
    }
}
