package kn.org.deliverybackend.dto.ordernote;

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
public class OrderNoteDTO {
    private UUID id;
    private UUID orderId;
    private String body;
    private String author;
    private LocalDateTime createdAt;
}
