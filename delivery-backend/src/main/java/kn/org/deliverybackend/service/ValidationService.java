package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.request.auth.LoginRequest;
import kn.org.deliverybackend.dto.request.auth.RegistrationRequest;

/**
 * Service for validating user input for registration and login.
 * Implements requirements 4.1-4.6 and 5.1-5.2 for input validation.
 */
public interface ValidationService {
    
    /**
     * Validates registration input data.
     * Checks for empty fields, email format, and password length.
     * 
     * @param request the registration request to validate
     * @throws IllegalArgumentException if validation fails with specific error message
     */
    void validateRegistrationInput(RegistrationRequest request);
    
    /**
     * Validates login input data.
     * Checks for empty username and password.
     * 
     * @param request the login request to validate
     * @throws IllegalArgumentException if validation fails with specific error message
     */
    void validateLoginInput(LoginRequest request);
    
    /**
     * Validates email format using basic email regex.
     * 
     * @param email the email address to validate
     * @return true if email format is valid, false otherwise
     */
    boolean validateEmailFormat(String email);
}
