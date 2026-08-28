package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.dto.discount.DiscountResponseDTO;
import kn.org.deliverybackend.service.DiscountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public, read-only list of the currently-active automatic discounts so the
 * storefront can show effective (discounted) prices on product cards and the
 * product page. Anonymous — see SecurityConfig chain 2.
 */
@RestController
@RequestMapping("/api/discounts")
@RequiredArgsConstructor
@Tag(name = "Discounts")
public class DiscountController {

    private final DiscountService discountService;

    @GetMapping("/active")
    public ResponseEntity<List<DiscountResponseDTO>> active() {
        return ResponseEntity.ok(discountService.activePublic());
    }
}
