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
    @Size(max = 1000)
    private String deliveryAddress;

    @Size(max = 40)
    private String phone;
}
