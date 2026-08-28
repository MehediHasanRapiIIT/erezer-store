package kn.org.deliverybackend.dto.returns;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnPhotoDTO {
    private Long id;
    private String url;
    private String caption;
}
