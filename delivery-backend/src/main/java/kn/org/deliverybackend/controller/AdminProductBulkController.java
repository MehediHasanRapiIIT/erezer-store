package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.dto.request.product.BulkProductActionDTO;
import kn.org.deliverybackend.enumeration.BulkProductAction;
import kn.org.deliverybackend.service.impl.ProductBulkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Do this to the ticked products" on the Products page.
 *
 * <p>One address for every action, so the permission is decided per action and
 * is the same one the action needs on a single product: deleting needs "Delete
 * products", the home-page flags need "Feature products", "Never discount"
 * needs the discount switches, and the rest need "Edit products".
 */
@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
@Tag(name = "Admin: Products in bulk")
public class AdminProductBulkController {

    private final ProductBulkService productBulkService;

    @RequiresPermission(value = {Perm.PRODUCTS_EDIT, Perm.PRODUCTS_DELETE, Perm.PRODUCTS_FEATURE, Perm.DISCOUNTS_SWITCHES},
            mode = RequiresPermission.Mode.ANY)
    @PutMapping("/bulk")
    public ResponseEntity<ProductBulkService.Result> bulk(@Valid @RequestBody BulkProductActionDTO request) {
        StaffAccess.require(permissionFor(request.getAction()));
        ProductBulkService.Result result = productBulkService.apply(request);
        StaffAccess.describe(result.message());
        return ResponseEntity.ok(result);
    }

    /** The permission the same action needs on one product. */
    static Perm permissionFor(BulkProductAction action) {
        return switch (action) {
            case DELETE -> Perm.PRODUCTS_DELETE;
            case FEATURE, UNFEATURE, NEW_ARRIVAL_ON, NEW_ARRIVAL_OFF -> Perm.PRODUCTS_FEATURE;
            case NEVER_DISCOUNT_ON, NEVER_DISCOUNT_OFF -> Perm.DISCOUNTS_SWITCHES;
            case MOVE_CATEGORY, SHOW, HIDE, STOCK_SHOW_QUANTITY, STOCK_SHOW_LABELS, SET_SIZE_CHART -> Perm.PRODUCTS_EDIT;
        };
    }
}
