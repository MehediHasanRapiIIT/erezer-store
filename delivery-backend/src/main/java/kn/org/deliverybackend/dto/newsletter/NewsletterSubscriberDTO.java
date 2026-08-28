package kn.org.deliverybackend.dto.newsletter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsletterSubscriberDTO {
    private UUID id;
    private String email;
    private String status;
    private String source;
    private LocalDateTime subscribedAt;
    private LocalDateTime unsubscribedAt;
}
