package kn.org.deliverybackend.dto.customdesign;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** A garment as exposed to the storefront studio. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomDesignItemDTO {
    private String name;
    private String category;
    private List<String> sizes;
    private List<String> printTechniques;
    private List<CustomDesignColorDTO> colors;
}
