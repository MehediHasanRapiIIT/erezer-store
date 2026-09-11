package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.exception.PermissionDeniedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import kn.org.deliverybackend.access.RequiresPermission;
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

    @RequiresPermission(Perm.SETTINGS_VIEW)
    @GetMapping
    public ResponseEntity<StoreSettingsDTO> get() {
        return ResponseEntity.ok(storeSettingsService.get());
    }

    @RequiresPermission(value = {Perm.SETTINGS_STORE, Perm.SETTINGS_HOMEPAGE, Perm.SETTINGS_FOOTER, Perm.SETTINGS_SIZECHART, Perm.SETTINGS_PAYMENTS}, mode = RequiresPermission.Mode.ANY)
    @PutMapping
    public ResponseEntity<StoreSettingsDTO> update(@RequestBody StoreSettingsDTO request) {
        requireSectionPermissions(storeSettingsService.get(), request);
        return ResponseEntity.ok(storeSettingsService.update(request));
    }

    /**
     * The settings page saves every section at once, so each section is
     * checked on its own: one the person may not edit must come back unchanged.
     * The discount switches are not saved here at all; see the discounts page.
     */
    private static void requireSectionPermissions(StoreSettingsDTO current, StoreSettingsDTO request) {
        List<Perm> changed = new ArrayList<>();
        track(changed, Perm.SETTINGS_STORE,
                sameText(current.getReturnPolicyText(), request.getReturnPolicyText())
                        && Objects.equals(current.getExchangeWindowDays(), request.getExchangeWindowDays())
                        && sameText(current.getSupportPhone(), request.getSupportPhone())
                        && sameText(current.getSupportEmail(), request.getSupportEmail())
                        && sameText(current.getSupportHours(), request.getSupportHours()));
        track(changed, Perm.SETTINGS_HOMEPAGE,
                Objects.equals(current.getBrandStory(), request.getBrandStory())
                        && Objects.equals(current.getMarquee(), request.getMarquee())
                        && Objects.equals(current.getHighlights(), request.getHighlights()));
        track(changed, Perm.SETTINGS_FOOTER, Objects.equals(current.getFooter(), request.getFooter()));
        track(changed, Perm.SETTINGS_SIZECHART, Objects.equals(current.getSizeChart(), request.getSizeChart()));
        track(changed, Perm.SETTINGS_PAYMENTS,
                on(current.getPaymentCodEnabled()) == on(request.getPaymentCodEnabled())
                        && on(current.getPaymentBkashEnabled()) == on(request.getPaymentBkashEnabled())
                        && on(current.getPaymentCardEnabled()) == on(request.getPaymentCardEnabled()));

        Perm[] missing = changed.stream().filter(p -> !StaffAccess.has(p)).toArray(Perm[]::new);
        if (missing.length > 0) throw new PermissionDeniedException(missing);

        if (changed.isEmpty()) {
            StaffAccess.describe("Saved store settings (nothing changed)");
        } else {
            StaffAccess.markUsed(changed.get(0));
            StaffAccess.describe("Changed store settings: "
                    + String.join(", ", changed.stream().map(AdminStoreSettingsController::sectionName).toList()));
        }
    }

    private static void track(List<Perm> changed, Perm section, boolean unchanged) {
        if (!unchanged) changed.add(section);
    }

    private static String sectionName(Perm section) {
        return switch (section) {
            case SETTINGS_STORE -> "policies and support contacts";
            case SETTINGS_HOMEPAGE -> "home page content";
            case SETTINGS_FOOTER -> "footer";
            case SETTINGS_SIZECHART -> "size chart";
            case SETTINGS_PAYMENTS -> "payment methods";
            default -> section.label();
        };
    }

    /** Blank and missing text count as the same. */
    private static boolean sameText(String a, String b) {
        return Objects.equals(a == null || a.isBlank() ? null : a, b == null || b.isBlank() ? null : b);
    }

    /** A missing payment flag means on, as when saving. */
    private static boolean on(Boolean flag) {
        return flag == null || flag;
    }
}
