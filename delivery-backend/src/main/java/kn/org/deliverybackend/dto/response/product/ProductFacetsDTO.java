package kn.org.deliverybackend.dto.response.product;

import java.math.BigDecimal;
import java.util.List;

/**
 * The shop's filter choices for a search and category: the genders and brands
 * that have products, and the highest price shown (null when nothing matches).
 */
public record ProductFacetsDTO(List<String> genders, List<String> brands, BigDecimal maxPrice) {}
