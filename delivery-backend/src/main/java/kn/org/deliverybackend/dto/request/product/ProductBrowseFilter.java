package kn.org.deliverybackend.dto.request.product;

import java.math.BigDecimal;

/**
 * What the shop list is narrowed by. Every field is optional: {@code q}
 * searches name, brand and description; {@code maxPrice} applies to the
 * price customers see (the sale price when there is one).
 */
public record ProductBrowseFilter(String q, Long categoryId, String gender, String brand, BigDecimal maxPrice) {}
