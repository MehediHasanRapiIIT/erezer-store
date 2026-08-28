package kn.org.deliverybackend.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** A titled column of links in the footer. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FooterColumnDTO {
    private String title;
    private List<FooterLinkDTO> links;
}
