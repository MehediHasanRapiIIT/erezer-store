package kn.org.deliverybackend.dto.request.cart;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddToCartRequestDTO {

    @NotNull
    private Long productId;

    // Optional — the specific size/colour variant being added. Null = base product.
    private Long variantId;

    // The following three are authoritative on the server for single add-to-cart
    // (resolved from productId/variantId) and only consulted for guest-cart merge,
    // so they are optional here.
    private String productName;

    private String imageUrl;

    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal unitPrice;

    @Min(1)
    private Integer quantity; // defaults to 1 in service if null
}