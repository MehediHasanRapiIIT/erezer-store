package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.service.impl.ShowcaseSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The in-store showcase screen: read by the screen itself; changed on the Settings page. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Showcase screen")
public class ShowcaseSettingsController {

    private final ShowcaseSettingsService settings;

    @GetMapping("/api/showcase")
    public ShowcaseSettingsService.Settings get() {
        return settings.get();
    }

    @RequiresPermission(Perm.SETTINGS_VIEW)
    @GetMapping("/admin/showcase")
    public ShowcaseSettingsService.Settings getForAdmin() {
        return settings.get();
    }

    @RequiresPermission(Perm.SETTINGS_STORE)
    @PutMapping("/admin/showcase")
    public ShowcaseSettingsService.Settings save(@RequestBody ShowcaseSettingsService.Settings change) {
        ShowcaseSettingsService.Settings saved = settings.save(change);
        StaffAccess.describe("Changed the showcase screen (" + (saved.enabled() ? "on" : "off")
                + ", " + saved.slideSeconds() + "s a slide, " + saved.productMode().toLowerCase() + " products)");
        return saved;
    }
}
