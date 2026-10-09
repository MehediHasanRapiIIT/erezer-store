package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.dto.variant.ProductOptionDTO;
import kn.org.deliverybackend.service.impl.ProductOptionsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A product's own options: colour, and anything else the shop defines.
 *
 * <p>Reading them is open, as the product page needs them. Changing them
 * changes which variants the product has, so it needs the same permission as
 * the sizes list.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Product options")
public class ProductOptionsController {

    private final ProductOptionsService productOptions;

    @GetMapping("/api/products/{productId}/options")
    public List<ProductOptionDTO> options(@PathVariable Long productId) {
        return productOptions.get(productId);
    }

    /**
     * Sets the options to exactly these and answers with the product's variants
     * as they now stand. {@code restoreMissing} also puts back combinations
     * that were removed earlier.
     */
    @RequiresPermission(Perm.PRODUCTS_VARIANTS)
    @PutMapping("/admin/products/{productId}/options")
    public ResponseEntity<ProductOptionsService.Result> setOptions(
            @PathVariable Long productId,
            @RequestBody List<ProductOptionDTO> options,
            @RequestParam(defaultValue = "false") boolean restoreMissing) {
        ProductOptionsService.Result result = productOptions.set(productId, options, restoreMissing);
        StaffAccess.describe(result.options().isEmpty()
                ? "Removed the options of product " + productId
                : "Set the options of product " + productId + " to "
                    + String.join(", ", result.options().stream()
                        .map(o -> o.getName() + " (" + o.getValues().size() + ")").toList())
                    + ": " + result.variants().size() + " variants");
        return ResponseEntity.ok(result);
    }
}
