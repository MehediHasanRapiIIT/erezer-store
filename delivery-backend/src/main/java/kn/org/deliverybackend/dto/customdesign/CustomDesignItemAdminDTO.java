package kn.org.deliverybackend.dto.customdesign;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A designable garment for admin read/upsert. On write, the {@code colors},
 * {@code sizes} and {@code printTechniques} lists fully replace the stored ones.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomDesignItemAdminDTO {

    /** Null when creating a new garment. */
    private UUID id;

    @NotBlank
    @Size(max = 150)
    private String name;

    @Size(max = 100)
    private String category;

    private Integer sortOrder;

    private Boolean active;

    @Builder.Default
    private List<@Size(max = 40) String> sizes = new ArrayList<>();

    @Builder.Default
    private List<@Size(max = 80) String> printTechniques = new ArrayList<>();

    @Valid
    @Builder.Default
    private List<CustomDesignColorAdminDTO> colors = new ArrayList<>();
}
