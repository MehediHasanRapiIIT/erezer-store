package kn.org.deliverybackend.dto.customdesign;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** A logo-library entry for admin read/upsert. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomDesignLogoAdminDTO {

    /** Null when creating. */
    private UUID id;

    @NotBlank
    @Size(max = 200)
    private String name;

    @NotBlank
    @Size(max = 1000)
    private String url;

    private Integer sortOrder;

    private Boolean active;
}
