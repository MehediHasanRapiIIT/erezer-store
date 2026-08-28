package kn.org.deliverybackend.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponseDTO {
    private String message;

    public static MessageResponseDTO of(String message) {
        return new MessageResponseDTO(message);
    }
}
