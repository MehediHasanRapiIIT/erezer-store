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

    @jakarta.validation.constraints.Size(max = 255)
    private String email;

    @NotBlank

    @jakarta.validation.constraints.Size(max = 100)
    private String firstName;

    @NotBlank

    @jakarta.validation.constraints.Size(max = 100)
    private String lastName;

    @NotBlank

    @jakarta.validation.constraints.Size(max = 255, message = "Delivery address must be 255 characters or fewer")
    private String deliveryAddress;

    /** Contact phone the guest entered at checkout. */
    @jakarta.validation.constraints.NotBlank(message = "Phone number is required")
    @jakarta.validation.constraints.Pattern(regexp = "^[0-9+\\-\\s()]{7,20}$", message = "Enter a valid phone number")
    private String phone;


    @jakarta.validation.constraints.NotBlank(message = "Payment method is required")

    @jakarta.validation.constraints.Pattern(regexp = "(?i)^(CASH|COD|BKASH|CARD)$", message = "Choose cash on delivery, bKash or card")
    private String paymentMethod;

    private Long shopId;

    private Double shippingFee;

    @NotEmpty
    @Valid

    @jakarta.validation.constraints.Size(max = 50, message = "An order can have up to 50 lines")
    private List<OrderItemRequestDTO> items;

    /** Optional coupon code; re-validated server-side. */
    @jakarta.validation.constraints.Size(max = 64)
    private String couponCode;

    /** Optional explicit zone (else resolved from deliveryAddress). */
    private Long shippingZoneId;

    /** Optional bundle offer id — when set, the items are priced as that bundle. */
    private UUID bundleId;
}
