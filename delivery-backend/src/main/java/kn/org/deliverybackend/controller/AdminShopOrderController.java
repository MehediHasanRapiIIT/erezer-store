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
