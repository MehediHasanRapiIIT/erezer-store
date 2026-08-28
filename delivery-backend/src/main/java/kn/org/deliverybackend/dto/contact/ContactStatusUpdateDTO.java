package kn.org.deliverybackend.dto.contact;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactStatusUpdateDTO {

    /** NEW | READ | RESOLVED */
    @NotBlank
    private String status;
}
