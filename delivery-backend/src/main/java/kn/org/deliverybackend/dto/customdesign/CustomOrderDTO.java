package kn.org.deliverybackend.dto.customdesign;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Full custom-order view for the admin detail screen. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomOrderDTO {
    private UUID id;
    private String reference;
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private String shippingAddress;
    private String apartment;
    private String city;
    private String zipCode;
    private String country;
    private String notes;
    private String itemName;
    private String colorName;
    private String size;
    private String printTechnique;
    private String designJson;
    private String status;
    private String adminNotes;
    private List<CustomOrderImageDTO> images;
    private LocalDateTime createdAt;
}
