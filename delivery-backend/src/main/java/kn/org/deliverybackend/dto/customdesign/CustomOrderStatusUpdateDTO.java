package kn.org.deliverybackend.dto.customdesign;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Admin status change (and optional internal note) for a custom order. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomOrderStatusUpdateDTO {

    /** One of CustomOrderStatus: NEW | IN_REVIEW | QUOTED | CONFIRMED | CLOSED. */
    @NotBlank
    private String status;

    @Size(max = 20000)
    private String adminNotes;
}
