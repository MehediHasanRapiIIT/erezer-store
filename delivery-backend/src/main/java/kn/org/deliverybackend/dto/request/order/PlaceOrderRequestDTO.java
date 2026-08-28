package kn.org.deliverybackend.dto.request.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class PlaceOrderRequestDTO {

    private UUID clientId;

    private String deliveryAddress;

    /** Contact phone the customer entered at checkout (falls back to their profile). */
    private String phone;

    private String paymentMethod;

    private Long shopId;

    private Double deliveryCharge;

    private Float latitude;

    private Float longitude;

    @NotEmpty
    @Valid
    private List<OrderItemRequestDTO> items;

    // ── Phase 4 ────────────────────────────────────────────────────────────────
    /** Optional coupon code entered by the customer. Re-validated server-side. */
    private String couponCode;

    /** Optional explicit zone choice (e.g. "Inside Dhaka"); else resolved from deliveryAddress. */
    private Long shippingZoneId;

    /** Optional bundle offer id — when set, the items are priced as that bundle. */
    private UUID bundleId;
}
