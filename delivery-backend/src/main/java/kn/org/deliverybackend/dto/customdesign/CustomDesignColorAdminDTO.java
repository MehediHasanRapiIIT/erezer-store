package kn.org.deliverybackend.dto.customdesign;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** A colourway for admin read/upsert. Mockup image URLs come from prior uploads. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomDesignColorAdminDTO {

    /** Null when creating a new colour. */
    private UUID id;

    @NotBlank
    @Size(max = 80)
    private String name;

    @NotBlank
    @Size(max = 9)
    private String hex;

    @Size(max = 1000)
    private String frontImageUrl;

    @Size(max = 1000)
    private String backImageUrl;

    @Size(max = 1000)
    private String leftSleeveImageUrl;

    @Size(max = 1000)
    private String rightSleeveImageUrl;

    private Integer sortOrder;
}
