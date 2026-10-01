package kn.org.deliverybackend.controller;

import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.dto.request.product.ProductRequestDTO;
import kn.org.deliverybackend.dto.response.product.ProductResponseDTO;
import kn.org.deliverybackend.dto.variant.VariantRequestDTO;
import kn.org.deliverybackend.dto.variant.VariantResponseDTO;
import kn.org.deliverybackend.service.ProductPricing;

import java.math.BigDecimal;

/**
 * Parts of a product, and of its sizes, that need a permission of their own.
 *
 * <p>Kept in one place because a product can be changed from three screens —
 * the product form, the sizes list, and "add product" which saves everything at
 * once — and each must ask for exactly the same permissions.
 */
final class ProductAccess {

    private ProductAccess() {}

    /**
     * What customers pay (price and sale discount), the home-page flags, and
     * "Never discount". {@code current} is null when adding; the price of a new
     * product is covered by the endpoint's own rule.
     */
    static void checkProductFields(ProductRequestDTO request, ProductResponseDTO current) {
        if (current != null) {
            BigDecimal salePriceBefore = current.getDiscountPrice() != null ? current.getDiscountPrice() : current.getPrice();
            BigDecimal salePriceAfter = request.requestedSalePrice();
            if (!ProductPricing.sameAmount(request.getPrice(), current.getPrice())
                    || !ProductPricing.sameAmount(salePriceAfter, salePriceBefore)) {
                StaffAccess.require(Perm.PRODUCTS_PRICE);
            }
        }
        if (flagChanged(request.getIsFeatured(), current == null ? null : current.getIsFeatured())
                || flagChanged(request.getIsNewArrival(), current == null ? null : current.getIsNewArrival())) {
            StaffAccess.require(Perm.PRODUCTS_FEATURE);
        }
        if (flagChanged(request.getDiscountExcluded(), current == null ? null : current.getDiscountExcluded())) {
            StaffAccess.require(Perm.DISCOUNTS_SWITCHES);
        }
    }

    /**
     * A size's own price is a price, and its stock is inventory, so changing
     * either needs that permission too. {@code current} is null when adding.
     */
    static void checkVariantFields(VariantRequestDTO request, VariantResponseDTO current) {
        if (!ProductPricing.sameAmount(request.getPriceOverride(), current == null ? null : current.getPriceOverride())) {
            StaffAccess.require(Perm.PRODUCTS_PRICE);
        }
        int stockBefore = current == null || current.getStockQuantity() == null ? 0 : current.getStockQuantity();
        if (request.getStockQuantity() != null && request.getStockQuantity() != stockBefore) {
            StaffAccess.require(Perm.INVENTORY_EDIT);
        }
    }

    /** True when a flag is sent and differs from what is stored. A flag not sent changes nothing; missing counts as off. */
    private static boolean flagChanged(Boolean requested, Boolean before) {
        return requested != null && requested != Boolean.TRUE.equals(before);
    }
}
