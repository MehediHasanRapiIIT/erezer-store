package kn.org.deliverybackend.dto.newsletter;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewsletterSubscribeRequestDTO {

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    /** Free-text source tag (e.g. STOREFRONT_FOOTER, CHECKOUT_OPTIN). */
    @Size(max = 64)
    private String source;
}
