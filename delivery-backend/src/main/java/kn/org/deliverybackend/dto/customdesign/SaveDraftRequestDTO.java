package kn.org.deliverybackend.dto.customdesign;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Create/update payload for a saved design draft. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveDraftRequestDTO {

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 150)
    private String itemName;

    @Size(max = 80)
    private String colorName;

    @Size(max = 1000)
    private String thumbnailUrl;

    @NotBlank
    private String designJson;
}
