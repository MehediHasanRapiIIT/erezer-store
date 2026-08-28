package kn.org.deliverybackend.dto;

import kn.org.deliverybackend.dto.response.product.ProductResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One admin-promoted category band on the landing page, e.g. "Erezer Pink".
 *
 * Carries its own products so the whole page still loads in the single
 * /app/home request rather than one round trip per section.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeCategorySectionDTO {

    private Long categoryId;
    private String name;
    /** Storefront URL for this collection, e.g. "erezer-pink" -> /erezer-pink. */
    private String slug;
    private String imageUrl;
    private Integer sortOrder;
    /** Products to show in the band; already trimmed to the display limit. */
    private List<ProductResponseDTO> products;
}
