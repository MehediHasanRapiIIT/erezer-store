package kn.org.deliverybackend.service.impl;

import kn.org.deliverybackend.dto.request.auth.LoginRequest;
import kn.org.deliverybackend.dto.request.auth.RegistrationRequest;
import kn.org.deliverybackend.service.ValidationService;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Implementation of ValidationService for validating user registration and login inputs.
 * Validates empty fields, email format, and password length according to requirements.
 */
@Service
public class ValidationServiceImpl implements ValidationService {
    
    // Basic email regex pattern: requires @ symbol with text before and after, and a domain with at least one dot
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    
    private static final int MIN_PASSWORD_LENGTH = 6;
    
    @Override
    public void validateRegistrationInput(RegistrationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Registration request cannot be null");
        }
        
        // Check for empty or whitespace-only fullName (Requirement 4.1)
        if (request.getFullName() == null || request.getFullName().trim().isEmpty()) {
            throw new IllegalArgumentException("Full name is required");
        }
        
        // Check for empty or whitespace-only username (Requirement 4.2)
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        
        // Check for empty or whitespace-only email (Requirement 4.3)
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        
        // Check email format (Requirement 4.4)
        if (!validateEmailFormat(request.getEmail())) {
            throw new IllegalArgumentException("Invalid email format");
        }
        
        // Check for empty or whitespace-only password (Requirement 4.5)
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        
        // Check password length (Requirement 4.6)
        if (request.getPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
    }
    
    @Override
    public void validateLoginInput(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Login request cannot be null");
        }
        
        // Check for empty username (Requirement 5.1)
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        
        // Check for empty password (Requirement 5.2)
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
    }
    
    @Override
    public boolean validateEmailFormat(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }
}
