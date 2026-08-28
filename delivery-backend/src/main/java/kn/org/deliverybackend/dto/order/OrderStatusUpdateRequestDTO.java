package kn.org.deliverybackend.dto.order;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusUpdateRequestDTO {

    /** Target status, must be one of OrderStatus enum names (case-insensitive). */
    @NotBlank
    private String status;

    /** Optional admin note; surfaced to the customer in the email. */
    private String note;

    /** Required when transitioning to SHIPPED — courier display name. */
    private String courierName;

    /** Required when transitioning to SHIPPED — courier-issued tracking number. */
    private String trackingNumber;
}
