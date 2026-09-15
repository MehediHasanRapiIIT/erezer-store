package kn.org.deliverybackend.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One block of the About page: a heading, its text, and an optional photo. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AboutSectionDTO {
    private String heading;
    private String body;
    private String imageUrl;
}
