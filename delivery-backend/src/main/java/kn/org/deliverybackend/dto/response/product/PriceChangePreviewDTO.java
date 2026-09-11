package kn.org.deliverybackend.dto.response.product;

import java.math.BigDecimal;
import java.util.List;

/**
 * What a category price change does (preview) or did (apply), product by
 * product. The counts and {@code changeableIds} always cover the whole
 * category, so ticking and "Apply to N products" work across pages; {@code rows}
 * is one page of the products matching the search (every product, after apply).
 * Sale prices are null when the product is not on sale.
 */
public record PriceChangePreviewDTO(
        Long categoryId,
        String categoryName,
        int productCount,
        int changedCount,
        int problemCount,
        /** Every product in the category that would change and can be changed: the ones ticked by default. */
        List<Long> changeableIds,
        int page,
        int size,
        /** Products matching the search, over all pages. */
        int totalRows,
        int totalPages,
        List<Row> rows) {

    /** One product. {@code problem} says why it can't be changed; null when it can. */
    public record Row(
            Long productId,
            String name,
            String sku,
            String productCode,
            String imageUrl,
            BigDecimal oldPrice,
            BigDecimal newPrice,
            BigDecimal oldSalePrice,
            BigDecimal newSalePrice,
            List<SizeRow> sizes,
            boolean changed,
            String problem) {
    }

    /** A size with its own price. */
    public record SizeRow(Long variantId, String size, BigDecimal oldPrice, BigDecimal newPrice) {
    }
}
