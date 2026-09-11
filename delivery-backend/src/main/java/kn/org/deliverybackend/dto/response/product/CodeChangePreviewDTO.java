package kn.org.deliverybackend.dto.response.product;

import java.util.List;

/**
 * What giving a category its codes does (preview) or did (apply). The counts
 * cover the whole category whatever the page or search; {@code rows} is one
 * page of the products matching the search (every product, after apply).
 * Products are listed and numbered in name order.
 */
public record CodeChangePreviewDTO(
        Long categoryId,
        String categoryName,
        int productCount,
        int changedCount,
        int problemCount,
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
            String imageUrl,
            String oldCode,
            String newCode,
            boolean changed,
            String problem) {
    }
}
