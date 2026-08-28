package kn.org.deliverybackend.dto.customdesign;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A colourway as exposed to the storefront studio. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomDesignColorDTO {
    private String name;
    private String hex;
    private CustomDesignImagesDTO images;
}
