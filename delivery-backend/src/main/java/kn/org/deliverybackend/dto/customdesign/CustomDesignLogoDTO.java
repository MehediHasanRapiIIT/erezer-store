package kn.org.deliverybackend.dto.customdesign;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A library clipart/logo the customer can add to their design. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomDesignLogoDTO {
    private String name;
    private String url;
}
