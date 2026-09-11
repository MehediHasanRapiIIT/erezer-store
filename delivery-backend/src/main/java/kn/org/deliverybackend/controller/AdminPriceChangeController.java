package kn.org.deliverybackend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kn.org.deliverybackend.access.Perm;
import kn.org.deliverybackend.access.RequiresPermission;
import kn.org.deliverybackend.access.StaffAccess;
import kn.org.deliverybackend.dto.request.product.PriceChangeRequestDTO;
import kn.org.deliverybackend.dto.response.product.PriceChangePreviewDTO;
import kn.org.deliverybackend.dto.response.product.PriceChangePreviewDTO.Row;
import kn.org.deliverybackend.service.CategoryPriceChange;
import kn.org.deliverybackend.service.CategoryPriceChange.PriceMode;
import kn.org.deliverybackend.service.CategoryPriceChange.SaleMode;
import kn.org.deliverybackend.service.PriceChangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.stream.Collectors;

import static kn.org.deliverybackend.service.CategoryPriceChange.taka;

/**
 * Changing prices across a category (CATEGORY-PRICE-PLAN.md). Needs the same
 * permissions as changing one product's price: "Edit products" and "Change prices".
 */
@RestController
@RequestMapping("/admin/products/price-change")
@RequiredArgsConstructor
@Tag(name = "Admin: Products")
public class AdminPriceChangeController {

    private final PriceChangeService priceChangeService;

    /**
     * What the change would do, one page at a time; {@code q} searches product
     * name and SKU. A read, so trying options isn't logged.
     */
    @RequiresPermission({Perm.PRODUCTS_EDIT, Perm.PRODUCTS_PRICE})
    @GetMapping("/preview")
    public PriceChangePreviewDTO preview(
            @RequestParam Long categoryId,
            @RequestParam PriceMode priceMode,
            @RequestParam(required = false) BigDecimal priceValue,
            @RequestParam SaleMode saleMode,
            @RequestParam(required = false) BigDecimal salePercent,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return priceChangeService.preview(
                new PriceChangeRequestDTO(categoryId, priceMode, priceValue, saleMode, salePercent, null), q, page, size);
    }

    /** Applies the change to the ticked products; the activity log keeps every old and new price. */
    @RequiresPermission({Perm.PRODUCTS_EDIT, Perm.PRODUCTS_PRICE})
    @PostMapping
    public PriceChangePreviewDTO apply(@Valid @RequestBody PriceChangeRequestDTO request) {
        PriceChangePreviewDTO result = priceChangeService.apply(request);
        StaffAccess.markUsed(Perm.PRODUCTS_PRICE);
        StaffAccess.describe("Changed prices in " + result.categoryName() + " (" + result.changedCount()
                + " of " + result.productCount() + " ticked products changed): "
                + CategoryPriceChange.describe(request.priceMode(), request.priceValue(),
                        request.saleMode(), request.salePercent()));
        StaffAccess.details(result.rows().stream()
                .filter(Row::changed)
                .map(AdminPriceChangeController::line)
                .collect(Collectors.joining("\n")));
        return result;
    }

    /** "Classic Hoodie [code H-001]: price ৳1,400 → ৳1,540; sale ৳1,260 → ৳1,386; size XXL ৳1,500 → ৳1,650". */
    static String line(Row row) {
        StringBuilder s = new StringBuilder(row.name());
        if (row.productCode() != null && !row.productCode().isBlank()) {
            s.append(" [code ").append(row.productCode()).append(']');
        }
        s.append(": price ").append(taka(row.oldPrice())).append(" → ").append(taka(row.newPrice()));
        if (row.oldSalePrice() != null || row.newSalePrice() != null) {
            s.append("; sale ").append(row.oldSalePrice() == null ? "none" : taka(row.oldSalePrice()))
                    .append(" → ").append(row.newSalePrice() == null ? "none" : taka(row.newSalePrice()));
        }
        row.sizes().forEach(size -> s.append("; size ").append(size.size()).append(' ')
                .append(taka(size.oldPrice())).append(" → ").append(taka(size.newPrice())));
        return s.toString();
    }
}
