package kn.org.deliverybackend.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Customer edit of an order's shipping address + phone (allowed only while PLACED). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderContactRequestDTO {

    @NotBlank
    @Size(max = 255)
    private String deliveryAddress;

    @Size(max = 40)

    @jakarta.validation.constraints.Pattern(regexp = "^[0-9+\\-\\s()]{7,20}$", message = "Enter a valid phone number")
    private String phone;
}
