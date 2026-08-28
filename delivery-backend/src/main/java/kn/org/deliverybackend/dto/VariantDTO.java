package kn.org.deliverybackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VariantDTO {

    private Long id;

    private Long productId;

    private Long categoryId;

    private Long quantity;

    private String name;

    private Long shopId;
}
