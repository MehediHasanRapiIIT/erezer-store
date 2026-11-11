package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.dto.payment.PaymentMethodsChangeDTO;
import kn.org.deliverybackend.service.impl.PaymentMethodsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Which ways to pay chosen products, or a whole category, may be bought with.
 * Driven from the admin Products page; the shop-wide switches stay in Settings.
 */
@RestController
@RequestMapping("/admin/payment-methods")
@RequiredArgsConstructor
@Tag(name = "Admin: Payment methods")
public class AdminPaymentMethodsController {

    private final PaymentMethodsService paymentMethods;

    @RequiresPermission(Perm.PRODUCTS_EDIT)
    @PutMapping("/products")
    public ResponseEntity<Map<String, String>> setOnProducts(@Valid @RequestBody PaymentMethodsChangeDTO change) {
        String message = paymentMethods.setOnProducts(change);
        StaffAccess.describe(message);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @RequiresPermission(Perm.CATEGORIES_EDIT)
    @PutMapping("/category")
    public ResponseEntity<Map<String, String>> setOnCategory(@Valid @RequestBody PaymentMethodsChangeDTO change) {
        String message = paymentMethods.setOnCategory(change);
        StaffAccess.describe(message);
        return ResponseEntity.ok(Map.of("message", message));
    }
}
