package kn.org.deliverybackend.dto.customdesign;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Everything the studio needs to render: designable garments + the logo library. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomDesignAssetsDTO {
    private List<CustomDesignItemDTO> items;
    private List<CustomDesignLogoDTO> logos;
}
