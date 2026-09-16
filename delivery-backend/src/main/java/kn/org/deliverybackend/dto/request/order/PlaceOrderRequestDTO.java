package kn.org.deliverybackend.dto.request.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class PlaceOrderRequestDTO {

    private UUID clientId;


    @jakarta.validation.constraints.NotBlank(message = "Delivery address is required")

    @jakarta.validation.constraints.Size(max = 255, message = "Delivery address must be 255 characters or fewer")
    private String deliveryAddress;

    /** Contact phone the customer entered at checkout (falls back to their profile). */
    @jakarta.validation.constraints.NotBlank(message = "Phone number is required")
    @jakarta.validation.constraints.Pattern(regexp = "^[0-9+\\-\\s()]{7,20}$", message = "Enter a valid phone number")
    private String phone;


    @jakarta.validation.constraints.NotBlank(message = "Payment method is required")

    @jakarta.validation.constraints.Pattern(regexp = "(?i)^(CASH|COD|BKASH|CARD)$", message = "Choose cash on delivery, bKash or card")
    private String paymentMethod;

    private Long shopId;

    private Double deliveryCharge;

    private Float latitude;

    private Float longitude;

    @NotEmpty
    @Valid

    @jakarta.validation.constraints.Size(max = 50, message = "An order can have up to 50 lines")
    private List<OrderItemRequestDTO> items;

    // ── Phase 4 ────────────────────────────────────────────────────────────────
    /** Optional coupon code entered by the customer. Re-validated server-side. */
    @jakarta.validation.constraints.Size(max = 64)
    private String couponCode;

    /** Optional explicit zone choice (e.g. "Inside Dhaka"); else resolved from deliveryAddress. */
    private Long shippingZoneId;

    /** Optional bundle offer id — when set, the items are priced as that bundle. */
    private UUID bundleId;
}
