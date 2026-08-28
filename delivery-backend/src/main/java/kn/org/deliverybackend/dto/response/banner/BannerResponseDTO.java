package kn.org.deliverybackend.dto.response.banner;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BannerResponseDTO {

    private Long id;

    private String name;

    private String imageUrl;

    private Boolean isActive;
}
