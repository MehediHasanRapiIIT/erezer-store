package kn.org.deliverybackend.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A footer link. {@code url} may be blank for plain text (e.g. an email line). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FooterLinkDTO {
    private String label;
    private String url;
}
