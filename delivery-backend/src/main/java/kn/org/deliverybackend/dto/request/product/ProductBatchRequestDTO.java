package kn.org.deliverybackend.dto.request.product;

import kn.org.deliverybackend.dto.variant.VariantRequestDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Several products added at once that share most of what they say: one
 * category, description, price, discount and set of sizes, with a name, code,
 * pictures and (if it differs) a price of their own.
 *
 * <p>Not validated as it arrives: each row is first made into an ordinary
 * product request and checked against exactly the rules a single product
 * obeys, so a mistake is reported against the row it is in.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductBatchRequestDTO {

    /** What every product has in common. Its name and code are ignored — those are per row. */
    private ProductRequestDTO shared;

    /** The same sizes, with the same stock, for every product. Optional. */
    private List<VariantRequestDTO> sizes;

    private List<Item> items;

    /** One product. Its pictures arrive as the multipart parts "pictures-&lt;row index&gt;". */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String name;
        private String productCode;
        /** This product's own price, when it differs from the shared one. */
        private BigDecimal price;
    }
}
