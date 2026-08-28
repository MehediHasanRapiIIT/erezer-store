package kn.org.deliverybackend.dto.customdesign;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** A saved design draft. {@code designJson} is omitted from list responses. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomDesignDraftDTO {
    private UUID id;
    private String name;
    private String itemName;
    private String colorName;
    private String thumbnailUrl;
    private String designJson;
    private String shareToken;
    private LocalDateTime updatedAt;
}
