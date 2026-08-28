package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.dto.coupon.CouponValidateRequestDTO;
import kn.org.deliverybackend.dto.coupon.CouponValidateResponseDTO;
import kn.org.deliverybackend.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
@Tag(name = "Coupons (public)")
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/validate")
    public ResponseEntity<CouponValidateResponseDTO> validate(
            @Valid @RequestBody CouponValidateRequestDTO request) {
        return ResponseEntity.ok(couponService.validate(request));
    }
}
