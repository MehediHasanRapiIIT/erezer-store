package kn.org.deliverybackend.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A physical store location ("Our outlets") shown in the footer: an uploaded
 * image, the outlet name, its address and a contact phone. A customer who
 * presses the outlet is taken to it on Google Maps.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FooterOutletDTO {
    private String imageUrl;
    private String name;
    private String address;
    private String phone;
    /**
     * The outlet's own Google Maps link (from "Share" in Google Maps). Optional:
     * without it the shop searches Google Maps for the name and address.
     */
    private String mapUrl;
}
