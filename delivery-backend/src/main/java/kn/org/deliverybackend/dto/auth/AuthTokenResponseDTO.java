package kn.org.deliverybackend.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthTokenResponseDTO {

    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private Boolean emailVerified;

    private String accessToken;
    private String refreshToken;
    private long expiresInSeconds;
    private String tokenType;
}
