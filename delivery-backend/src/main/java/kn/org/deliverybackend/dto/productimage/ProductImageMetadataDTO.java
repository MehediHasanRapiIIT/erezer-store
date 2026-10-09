package kn.org.deliverybackend.dto.productimage;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageMetadataDTO {

    @Size(max = 255)
    private String altText;

    @PositiveOrZero
    private Integer sortOrder;

    private Boolean isPrimary;
    /**
     * The choice this picture belongs to. An empty text means "all of them";
     * left out, it stays as it is.
     */
    @Size(max = 40)
    private String optionValueId;
}
