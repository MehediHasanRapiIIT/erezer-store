package kn.org.deliverybackend.dto.request.banner;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BannerRequestDTO {

    @NotBlank(message = "Banner name is required")
    private String name;

    private Boolean isActive;
}
