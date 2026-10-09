package kn.org.deliverybackend.dto.variant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One choice a variant is made of: "Colour: Black". */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VariantOptionDTO {
    private String optionId;
    /** "Colour" */
    private String option;
    private String valueId;
    /** "Black" */
    private String value;
    /** The swatch of a colour; null otherwise. */
    private String hex;
}
