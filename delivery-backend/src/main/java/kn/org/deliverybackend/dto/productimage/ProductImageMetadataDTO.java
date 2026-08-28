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
}
