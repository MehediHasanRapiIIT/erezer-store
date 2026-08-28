package kn.org.deliverybackend.dto.response.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for user registration endpoint.
 * Returns success status, user ID, and message.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationResponse {
    private boolean success;
    private Long userId;
    private String message;
    private String error;
    
    /**
     * Creates a success response with user ID.
     */
    public static RegistrationResponse success(Long userId) {
        return new RegistrationResponse(true, userId, "User registered successfully", null);
    }
    
    /**
     * Creates an error response with error message.
     */
    public static RegistrationResponse error(String errorMessage) {
        return new RegistrationResponse(false, null, null, errorMessage);
    }
}
