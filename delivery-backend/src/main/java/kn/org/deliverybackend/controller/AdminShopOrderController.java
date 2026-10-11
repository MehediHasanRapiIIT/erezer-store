package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.dto.response.product.ShopOrderItemDTO;
import kn.org.deliverybackend.service.impl.ShopOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The order products appear in on the Shop page and the category pages:
 * reading the ranked list, and saving a new one.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/shop-order")
@Tag(name = "Shop order")
public class AdminShopOrderController {

    private final ShopOrderService shopOrder;
    private final kn.org.deliverybackend.service.CategoryService categories;

    /** The body of a category save: the group (null for the main categories) and its categories, first to last. */
    public record CategoryOrderRequest(Long parentId, List<Long> categoryIds) {
    }

    /** Arranges the main categories, or one category's subcategories, in the shop. */
    @RequiresPermission(Perm.CATEGORIES_EDIT)
    @PutMapping("/categories")
    public List<kn.org.deliverybackend.dto.response.category.CategoryResponseDTO> saveCategories(@RequestBody CategoryOrderRequest request) {
        List<kn.org.deliverybackend.dto.response.category.CategoryResponseDTO> all =
                categories.arrangeSiblings(request == null ? null : request.parentId(), request == null ? null : request.categoryIds());
        StaffAccess.describe(request != null && request.parentId() != null
                ? "Changed the order of subcategories in the shop" : "Changed the order of categories in the shop");
        return all;
    }

    /** The body of a save: the ranked products' ids, first to last. */
    public record ShopOrderRequest(List<Long> productIds) {
    }

    @RequiresPermission(Perm.PRODUCTS_VIEW)
    @GetMapping
    public List<ShopOrderItemDTO> list() {
        return shopOrder.list();
    }

    @RequiresPermission(Perm.PRODUCTS_RANK)
    @PutMapping
    public List<ShopOrderItemDTO> save(@RequestBody ShopOrderRequest request) {
        List<ShopOrderItemDTO> saved = shopOrder.set(request == null ? null : request.productIds());
        StaffAccess.describe("Changed the order of products in the shop (" + saved.size() + " ranked)");
        return saved;
    }
}
