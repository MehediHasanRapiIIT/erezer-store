package kn.org.deliverybackend.dto.contact;

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
public class ContactMessageDTO {
    private UUID id;
    private String name;
    private String email;
    private String subject;
    private String message;
    private String status;
    private UUID orderId;
    private LocalDateTime createdAt;
}
