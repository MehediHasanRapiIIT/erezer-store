package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.dto.shipping.ShippingRulesChangeDTO;
import kn.org.deliverybackend.dto.shipping.ShippingSettingsDTO;
import kn.org.deliverybackend.dto.shipping.ShippingZoneDTO;
import kn.org.deliverybackend.dto.shipping.ZoneFeeChangeDTO;
import kn.org.deliverybackend.service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.text.DecimalFormat;

/** The admin Shipping page: zone prices and the free-shipping rules. */
@RestController
@RequestMapping("/admin/shipping")
@RequiredArgsConstructor
@Tag(name = "Admin: Shipping")
public class AdminShippingController {

    private final ShippingService shippingService;

    @RequiresPermission(Perm.SHIPPING_VIEW)
    @GetMapping
    public ResponseEntity<ShippingSettingsDTO> settings() {
        return ResponseEntity.ok(shippingService.settings());
    }

    @RequiresPermission(Perm.SHIPPING_EDIT)
    @PutMapping("/zones/{id}")
    public ResponseEntity<ShippingSettingsDTO> updateZoneFee(@PathVariable Long id,
                                                             @Valid @RequestBody ZoneFeeChangeDTO change) {
        ShippingSettingsDTO result = shippingService.updateZoneFee(id, change.flatFee());
        String zone = result.zones().stream().filter(z -> id.equals(z.getId()))
                .map(ShippingZoneDTO::getDisplayName).findFirst().orElse("zone " + id);
        StaffAccess.describe("Changed " + zone + " shipping to " + taka(change.flatFee()));
        return ResponseEntity.ok(result);
    }

    @RequiresPermission(Perm.SHIPPING_EDIT)
    @PutMapping("/rules")
    public ResponseEntity<ShippingSettingsDTO> updateRules(@RequestBody ShippingRulesChangeDTO change) {
        ShippingSettingsDTO result = shippingService.updateRules(change);
        if (change.freeAll() != null) {
            StaffAccess.describe(change.freeAll()
                    ? "Turned free shipping for all orders on" : "Turned free shipping for all orders off");
        } else if (change.offerEnabled() != null) {
            StaffAccess.describe(change.offerEnabled()
                    ? "Turned the free shipping offer on (orders from " + taka(result.offerMin()) + ")"
                    : "Turned the free shipping offer off");
        } else if (change.offerMin() != null) {
            StaffAccess.describe("Changed the free shipping offer to orders from " + taka(result.offerMin()));
        }
        return ResponseEntity.ok(result);
    }

    private static String taka(BigDecimal amount) {
        return amount == null ? "-" : "৳" + new DecimalFormat("#,##0.##").format(amount);
    }
}
