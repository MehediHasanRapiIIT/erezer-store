package kn.org.deliverybackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationsDTO {
    private UUID id;

    private UUID userId;

    private String titleEn;

    private String titleBn;

    private String messageEn;

    private String messageBn;

    private Boolean isRead;

    private LocalDateTime createdAt;
}
