package kn.org.deliverybackend.dto.customdesign;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A stored preview image for one view of a submitted custom order. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomOrderImageDTO {
    private String view;
    private String url;
}
