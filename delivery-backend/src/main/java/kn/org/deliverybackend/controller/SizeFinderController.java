package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.service.impl.SizeFinderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The size finder's rules: read by every product page; changed on the Size Charts page. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Size finder")
public class SizeFinderController {

    private final SizeFinderService finder;

    /** One table as it is saved. A missing switch or a missing list of sizes is left as it is. */
    public record TableRequest(Boolean enabled, List<SizeFinderService.Rule> rules) {
    }

    public record SwitchRequest(boolean enabled) {
    }

    @GetMapping("/api/size-finder")
    public SizeFinderService.Finder get() {
        return finder.finder();
    }

    @RequiresPermission(Perm.SETTINGS_SIZECHART)
    @PutMapping("/admin/size-finder/shop")
    public SizeFinderService.Finder setShop(@RequestBody SwitchRequest request) {
        SizeFinderService.Finder saved = finder.setShopEnabled(request.enabled());
        StaffAccess.describe(request.enabled() ? "Switched the size finder on" : "Switched the size finder off");
        return saved;
    }

    /** {@code scope}: GENERAL for products with no fit, or a fit (DROP_SHOULDER). */
    @RequiresPermission(Perm.SETTINGS_SIZECHART)
    @PutMapping("/admin/size-finder/{scope}")
    public SizeFinderService.Finder save(@PathVariable String scope, @RequestBody TableRequest request) {
        SizeFinderService.Finder saved = finder.save(scope, request.enabled(), request.rules());
        StaffAccess.describe("Changed the size finder for " + ("GENERAL".equalsIgnoreCase(scope) ? "products with no fit" : scope));
        return saved;
    }
}
