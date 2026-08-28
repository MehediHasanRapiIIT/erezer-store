package kn.org.deliverybackend.dto.request.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderItemRequestDTO {

    @NotNull
    private Long productId;

    @NotNull
    @Min(1)
    private Integer quantity;

    private Long variantId;

    /**
     * Custom (made-to-order) measurements as JSON, e.g.
     * {"Chest":38,"Length":40,"comments":"…"}. When present (and the product
     * enables custom sizing) the line is treated as made-to-order: the server
     * applies the product's flat surcharge and skips variant/stock checks.
     * The surcharge is never trusted from the client.
     */
    private String customMeasurements;
}
