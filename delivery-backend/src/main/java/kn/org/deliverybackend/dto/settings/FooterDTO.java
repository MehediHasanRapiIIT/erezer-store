package kn.org.deliverybackend.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Admin-editable storefront footer content. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FooterDTO {
    private String brandName;
    private String blurb;
    private List<FooterColumnDTO> columns;
    /** "Our promise" feature strip. */
    private List<FooterPromiseDTO> promises;
    /** "Our outlets" / store locations grid. */
    private List<FooterOutletDTO> outlets;
    private String copyright;
    private String tagline;
}
