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
public class NewsletterCampaignDTO {
    private UUID id;
    private String subject;
    private String bodyHtml;
    private String audience;
    private String status;
    private LocalDateTime sentAt;
    private String sentBy;
    private Integer sentCount;
    private Integer failCount;
    private LocalDateTime createdAt;
}
