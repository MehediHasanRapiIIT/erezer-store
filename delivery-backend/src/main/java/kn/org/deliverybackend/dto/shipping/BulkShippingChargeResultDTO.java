package kn.org.deliverybackend.dto.shipping;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** What a delivery-charge change did, for the message the admin sees. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkShippingChargeResultDTO {

    /** How many products had their own charge set or taken away. */
    private int products;

    /** How many categories had theirs set or taken away. */
    private int categories;

    /**
     * How many products in a category keep a charge of their own, and so are
     * unaffected by a change to the category.
     */
    private int keptOwnCharge;

    /** What was changed, e.g. "Saree" or "the whole shop". */
    private String scopeLabel;

    /** Plain-language summary, e.g. "Saree now costs ৳150 to deliver." */
    private String message;
}
