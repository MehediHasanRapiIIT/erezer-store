package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffAccess;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.coupon.CouponRequestDTO;
import kn.org.deliverybackend.dto.coupon.CouponResponseDTO;
import kn.org.deliverybackend.dto.coupon.CouponSwitchDTO;
import kn.org.deliverybackend.service.CouponService;
import kn.org.deliverybackend.service.StoreSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/coupons")
@RequiredArgsConstructor
@Tag(name = "Admin: Coupons")
public class AdminCouponController {

    private final CouponService couponService;
    private final StoreSettingsService storeSettingsService;

    /** Whether promo codes are on for the whole shop. */
    @RequiresPermission(Perm.COUPONS_VIEW)
    @GetMapping("/switch")
    public ResponseEntity<CouponSwitchDTO> promoSwitch() {
        return ResponseEntity.ok(storeSettingsService.getCouponSwitch());
    }

    /** Turns promo codes on or off for the whole shop. Coupons themselves are kept. */
    @RequiresPermission(Perm.COUPONS_SWITCH)
    @PutMapping("/switch")
    public ResponseEntity<CouponSwitchDTO> updatePromoSwitch(@RequestBody CouponSwitchDTO change) {
        CouponSwitchDTO result = storeSettingsService.updateCouponSwitch(change);
        if (change.couponsEnabled() != null) {
            StaffAccess.describe(change.couponsEnabled() ? "Turned promo codes on" : "Turned promo codes off");
        }
        return ResponseEntity.ok(result);
    }

    @RequiresPermission(Perm.COUPONS_VIEW)
    @GetMapping
    public ResponseEntity<List<CouponResponseDTO>> list() {
        return ResponseEntity.ok(couponService.list());
    }

    @RequiresPermission(Perm.COUPONS_CREATE)
    @PostMapping
    public ResponseEntity<CouponResponseDTO> create(@Valid @RequestBody CouponRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(couponService.create(request));
    }

    @RequiresPermission(Perm.COUPONS_EDIT)
    @PutMapping("/{id}")
    public ResponseEntity<CouponResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody CouponRequestDTO request) {
        return ResponseEntity.ok(couponService.update(id, request));
    }

    @RequiresPermission(Perm.COUPONS_DELETE)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        couponService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
