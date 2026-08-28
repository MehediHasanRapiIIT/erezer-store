package kn.org.deliverybackend.dto.customdesign;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The "Submit for Price" payload. Sent as the JSON {@code data} part of a
 * multipart request whose file parts are the flattened per-view preview PNGs.
 * Mirrors the modal: name / phone / email / shipping are required; apartment and
 * zip are optional; notes must carry the size-wise quantity and print technique.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomOrderRequestDTO {

    @NotBlank
    @Size(max = 120)
    private String firstName;

    @NotBlank
    @Size(max = 120)
    private String lastName;

    @NotBlank
    @Size(max = 40)
    private String phone;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @NotBlank
    @Size(max = 500)
    private String shippingAddress;

    @Size(max = 255)
    private String apartment;

    @NotBlank
    @Size(max = 150)
    private String city;

    @Size(max = 30)
    private String zipCode;

    @NotBlank
    @Size(max = 120)
    private String country;

    /** Rich-text (HTML) — required size-wise quantity + print/embroidery technique. */
    @NotBlank
    @Size(max = 20000)
    private String notes;

    // Studio selection snapshot (optional — design JSON is authoritative).
    @Size(max = 150)
    private String itemName;

    @Size(max = 80)
    private String colorName;

    @Size(max = 40)
    private String size;

    @Size(max = 80)
    private String printTechnique;

    /** Serialized fabric.js per-view canvas state, so the design can be reopened. */
    private String designJson;
}
