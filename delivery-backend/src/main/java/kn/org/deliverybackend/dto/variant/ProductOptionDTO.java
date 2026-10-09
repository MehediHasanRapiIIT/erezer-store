package kn.org.deliverybackend.dto.variant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * One option of a product ("Colour") with its choices ("Black", "White").
 *
 * <p>The ids are made by the server and never change, so a name can be edited
 * freely. Sent without an id, an option or a choice is a new one.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductOptionDTO {
    private String id;
    private String name;
    /** COLOUR (its choices carry a swatch) or TEXT. */
    private String kind;
    private List<Value> values = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Value {
        private String id;
        private String value;
        /** "#1F2937" for a colour's swatch; null otherwise. */
        private String hex;
    }
}
