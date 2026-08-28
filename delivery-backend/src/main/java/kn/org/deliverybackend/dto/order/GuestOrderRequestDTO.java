package kn.org.deliverybackend.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
public class GuestOrderRequestDTO {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotBlank
    private String deliveryAddress;

    /** Contact phone the guest entered at checkout. */
    private String phone;

    private String paymentMethod;

    private Long shopId;

    private Double shippingFee;

    @NotEmpty
    @Valid
    private List<OrderItemRequestDTO> items;

    /** Optional coupon code; re-validated server-side. */
    private String couponCode;

    /** Optional explicit zone (else resolved from deliveryAddress). */
    private Long shippingZoneId;

    /** Optional bundle offer id — when set, the items are priced as that bundle. */
    private UUID bundleId;
}
