package kn.org.deliverybackend.dto.response.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** What a bulk stock change did, for the message the admin sees. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkStockResultDTO {

    /** How many products the change covered. */
    private int updated;

    /** Of those, how many had less stock than was removed and are now 0. */
    private int setToZero;

    /** What the products have in common, e.g. "T-Shirts" or "the whole shop". */
    private String scopeLabel;

    /** Plain-language summary, e.g. "Added 10 to 12 products in T-Shirts.". */
    private String message;
}
