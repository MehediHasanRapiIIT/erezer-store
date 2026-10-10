package kn.org.deliverybackend.dto.response.product;

import java.math.BigDecimal;

/**
 * One product on the admin "Shop Order" list: its place in the shop and enough
 * about it to recognise it.
 */
public record ShopOrderItemDTO(Long id, int position, String name, String productCode, String sku, String imageUrl,
                               String categoryName, BigDecimal price, int stockQuantity, boolean available) {
}
