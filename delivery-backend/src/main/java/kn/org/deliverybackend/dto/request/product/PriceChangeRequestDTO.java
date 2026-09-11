package kn.org.deliverybackend.dto.request.product;

import jakarta.validation.constraints.NotNull;
import kn.org.deliverybackend.service.CategoryPriceChange.PriceMode;
import kn.org.deliverybackend.service.CategoryPriceChange.SaleMode;

import java.math.BigDecimal;
import java.util.List;

/**
 * A price change across one category. {@code priceValue} is the taka amount or
 * the percentage, depending on {@code priceMode}; {@code salePercent} is used
 * when {@code saleMode} is SET. {@code productIds} are the ticked products and
 * are needed to apply; a preview covers every product in the category.
 */
public record PriceChangeRequestDTO(
        @NotNull Long categoryId,
        @NotNull PriceMode priceMode,
        BigDecimal priceValue,
        @NotNull SaleMode saleMode,
        BigDecimal salePercent,
        List<Long> productIds) {
}
