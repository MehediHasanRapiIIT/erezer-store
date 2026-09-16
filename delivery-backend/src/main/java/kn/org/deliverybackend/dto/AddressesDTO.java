package kn.org.deliverybackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import kn.org.deliverybackend.enumeration.AddressType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO for address information")
public class AddressesDTO {

    private UUID id;
    @jakarta.validation.constraints.Size(max = 100)
    private String name;
    @NotBlank(message = "Address is required")
    @jakarta.validation.constraints.Size(max = 255, message = "Address must be 255 characters or fewer")
    private String address;
    private Float latitude;
    private Float longitude;
    private Long houseNumber;
    @jakarta.validation.constraints.Size(max = 255)
    private String apartmentName;
    private AddressType addressType;
    private UUID consumerId;
}
