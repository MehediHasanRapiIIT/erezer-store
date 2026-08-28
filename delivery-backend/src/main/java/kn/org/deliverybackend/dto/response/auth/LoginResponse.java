package kn.org.deliverybackend.dto.response.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for user login endpoint.
 * Returns success status, user information, and error message.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private boolean success;
    private UserResponse user;
    private String error;
    
    /**
     * Creates a success response with user information.
     */
    public static LoginResponse success(UserResponse user) {
        return new LoginResponse(true, user, null);
    }
    
    /**
     * Creates an error response with error message.
     */
    public static LoginResponse error(String errorMessage) {
        return new LoginResponse(false, null, errorMessage);
    }
}
