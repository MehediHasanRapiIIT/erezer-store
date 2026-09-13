package kn.org.deliverybackend.dto.contact;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactMessageRequestDTO {

    @NotBlank
    @Size(max = 200)
    private String name;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 255)
    private String subject;

    @NotBlank
    @Size(max = 4000)
    private String message;

    /**
     * Optional — when the customer is asking about a specific order. Text, not a
     * UUID: customers paste it the way they see it ("#<id>" on the Orders page,
     * the 8-character "#4a35228f" from an SMS), and a UUID field turned all of
     * those into an unreadable-body 400. The service resolves it to the order.
     */
    @Size(max = 64)
    private String orderId;
}
