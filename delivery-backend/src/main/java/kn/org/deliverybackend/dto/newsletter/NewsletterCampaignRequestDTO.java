package kn.org.deliverybackend.dto.newsletter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewsletterCampaignRequestDTO {

    @NotBlank
    @Size(max = 255)
    private String subject;

    /** Admin-authored HTML — rendered verbatim in the newsletter template. */
    @NotBlank
    private String bodyHtml;

    /** ALL_SUBSCRIBERS | REGISTERED_CUSTOMERS — defaults to ALL_SUBSCRIBERS when null. */
    private String audience;
}
