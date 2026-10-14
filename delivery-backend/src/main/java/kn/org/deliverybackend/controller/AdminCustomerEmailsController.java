package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.service.impl.CustomerEmailSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Settings page's "Customer emails" section. */
@RestController
@RequestMapping("/admin/customer-emails")
@RequiredArgsConstructor
@Tag(name = "Admin: Customer emails")
public class AdminCustomerEmailsController {

    private final CustomerEmailSettingsService settings;

    @RequiresPermission(Perm.SETTINGS_VIEW)
    @GetMapping
    public CustomerEmailSettingsService.Settings get() {
        return settings.get();
    }

    @RequiresPermission(Perm.SETTINGS_STORE)
    @PutMapping
    public CustomerEmailSettingsService.Settings save(@RequestBody CustomerEmailSettingsService.Settings change) {
        CustomerEmailSettingsService.Settings saved = settings.save(change);
        StaffAccess.describe("Changed the customer email settings"
                + " (cart reminder " + (saved.cartReminderEnabled() ? "on" : "off")
                + ", newsletter " + (saved.newsletterEnabled() ? "on" : "off") + ")");
        return saved;
    }
}
