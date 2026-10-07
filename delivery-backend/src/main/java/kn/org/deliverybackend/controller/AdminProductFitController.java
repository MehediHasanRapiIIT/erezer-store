package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.dto.variant.BulkFitsRequestDTO;
import kn.org.deliverybackend.dto.variant.ProductFitsRequestDTO;
import kn.org.deliverybackend.dto.variant.VariantResponseDTO;
import kn.org.deliverybackend.service.VariantService;
import kn.org.deliverybackend.service.impl.ProductFitsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The fits a product comes in — Drop Shoulder, Regular Fit, both or neither —
 * for one product or for many at once.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Admin: Product Fits")
public class AdminProductFitController {

    private final VariantService variantService;
    private final ProductFitsService productFitsService;

    /**
     * Sets one product's fits and returns its sizes as they now stand. A price
     * typed for a fit is a price, so it needs "Change prices" too.
     */
    @RequiresPermission(Perm.PRODUCTS_VARIANTS)
    @PutMapping("/products/{productId}/fits")
    public ResponseEntity<List<VariantResponseDTO>> setFits(@PathVariable Long productId,
                                                            @Valid @RequestBody ProductFitsRequestDTO request) {
        if (request.getFits().stream().anyMatch(ProductFitsRequestDTO.Choice::isChangePrice)) {
            StaffAccess.require(Perm.PRODUCTS_PRICE);
        }
        List<VariantResponseDTO> sizes = variantService.setFits(productId, request);
        StaffAccess.describe("Set the fits of product " + productId + " to "
                + (request.getFits().isEmpty() ? "none"
                    : String.join(", ", request.getFits().stream().map(ProductFitsRequestDTO.Choice::getFit).toList())));
        return ResponseEntity.ok(sizes);
    }

    /** The same fits for the chosen products or a whole category. Prices are left alone. */
    @RequiresPermission(Perm.PRODUCTS_VARIANTS)
    @PutMapping("/product-fits/bulk")
    public ResponseEntity<ProductFitsService.Result> setFitsForMany(@Valid @RequestBody BulkFitsRequestDTO request) {
        ProductFitsService.Result result = productFitsService.applyToMany(request);
        StaffAccess.describe(result.message());
        return ResponseEntity.ok(result);
    }
}
