package kn.org.deliverybackend.dto.checkout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import kn.org.deliverybackend.dto.request.order.OrderItemRequestDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutQuoteRequestDTO {

    @NotEmpty
    @Valid
    private List<OrderItemRequestDTO> items;

    /** Either {@code shippingZoneId} OR {@code deliveryAddress} must be present. */
    private Long shippingZoneId;
    private String deliveryAddress;

    /** Optional. When given, the quote applies the coupon if valid. */
    private String couponCode;

    /** Optional — enables per-user coupon limit enforcement. */
    private UUID userId;

    /**
     * Optional. When set, the items are priced as a bundle offer: the server
     * validates the selection and forces the items' total to the bundle's fixed
     * price (coupons/auto-discounts do not stack on a bundle).
     */
    private UUID bundleId;
}
