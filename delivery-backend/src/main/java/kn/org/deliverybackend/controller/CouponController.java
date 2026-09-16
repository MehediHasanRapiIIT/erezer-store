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
    private final kn.org.deliverybackend.service.RateLimiterService rateLimiter;

    @PostMapping("/validate")
    public ResponseEntity<CouponValidateResponseDTO> validate(
            @Valid @RequestBody CouponValidateRequestDTO request,
            jakarta.servlet.http.HttpServletRequest http) {
        // Slow enough that nobody can try code after code to find a working one.
        rateLimiter.enforce("coupon:" + kn.org.deliverybackend.util.ClientIp.of(http), 20, java.time.Duration.ofMinutes(1));
        return ResponseEntity.ok(couponService.validate(request));
    }
}
