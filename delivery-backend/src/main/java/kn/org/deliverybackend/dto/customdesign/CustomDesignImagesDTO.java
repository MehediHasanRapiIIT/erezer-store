package kn.org.deliverybackend.dto.customdesign;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The four garment mockup images (one per canvas view) for a colourway. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomDesignImagesDTO {
    private String front;
    private String back;
    private String leftSleeve;
    private String rightSleeve;
}
