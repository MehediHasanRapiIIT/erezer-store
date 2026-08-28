package kn.org.deliverybackend.dto.productimage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageDTO {
    private Long id;
    private Long productId;
    private String url;
    private String altText;
    private Integer sortOrder;
    private Boolean isPrimary;
}
