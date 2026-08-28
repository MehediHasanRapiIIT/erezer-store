package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.request.auth.LoginRequest;
import kn.org.deliverybackend.dto.request.auth.RegistrationRequest;
import kn.org.deliverybackend.service.impl.ValidationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ValidationService implementation.
 * Tests validation logic for registration and login inputs.
 */
class ValidationServiceTest {
    
    private ValidationService validationService;
    
    @BeforeEach
    void setUp() {
        validationService = new ValidationServiceImpl();
    }
    
    // Registration validation tests
    
    @Test
    void testValidateRegistrationInput_ValidData_NoException() {
        RegistrationRequest request = new RegistrationRequest(
            "John Doe",
            "johndoe",
            "john@example.com",
            "password123"
        );
        
        assertDoesNotThrow(() -> validationService.validateRegistrationInput(request));
    }
    
    @Test
    void testValidateRegistrationInput_NullRequest_ThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validationService.validateRegistrationInput(null)
        );
        assertEquals("Registration request cannot be null", exception.getMessage());
    }
    
    @Test
    void testValidateRegistrationInput_EmptyFullName_ThrowsException() {
        RegistrationRequest request = new RegistrationRequest(
            "",
            "johndoe",
            "john@example.com",
            "password123"
        );
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validationService.validateRegistrationInput(request)
        );
        assertEquals("Full name is required", exception.getMessage());
    }
    
    @Test
    void testValidateRegistrationInput_WhitespaceFullName_ThrowsException() {
        RegistrationRequest request = new RegistrationRequest(
            "   ",
            "johndoe",
            "john@example.com",
            "password123"
        );
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validationService.validateRegistrationInput(request)
        );
        assertEquals("Full name is required", exception.getMessage());
    }
    
    @Test
    void testValidateRegistrationInput_EmptyUsername_ThrowsException() {
        RegistrationRequest request = new RegistrationRequest(
            "John Doe",
            "",
            "john@example.com",
            "password123"
        );
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validationService.validateRegistrationInput(request)
        );
        assertEquals("Username is required", exception.getMessage());
    }
    
    @Test
    void testValidateRegistrationInput_EmptyEmail_ThrowsException() {
        RegistrationRequest request = new RegistrationRequest(
            "John Doe",
            "johndoe",
            "",
            "password123"
        );
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validationService.validateRegistrationInput(request)
        );
        assertEquals("Email is required", exception.getMessage());
    }
    
    @Test
    void testValidateRegistrationInput_InvalidEmailFormat_ThrowsException() {
        RegistrationRequest request = new RegistrationRequest(
            "John Doe",
            "johndoe",
            "notanemail",
            "password123"
        );
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validationService.validateRegistrationInput(request)
        );
        assertEquals("Invalid email format", exception.getMessage());
    }
    
    @Test
    void testValidateRegistrationInput_EmptyPassword_ThrowsException() {
        RegistrationRequest request = new RegistrationRequest(
            "John Doe",
            "johndoe",
            "john@example.com",
            ""
        );
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validationService.validateRegistrationInput(request)
        );
        assertEquals("Password is required", exception.getMessage());
    }
    
    @Test
    void testValidateRegistrationInput_ShortPassword_ThrowsException() {
        RegistrationRequest request = new RegistrationRequest(
            "John Doe",
            "johndoe",
            "john@example.com",
            "12345"
        );
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validationService.validateRegistrationInput(request)
        );
        assertEquals("Password must be at least 6 characters", exception.getMessage());
    }
    
    @Test
    void testValidateRegistrationInput_PasswordExactly6Characters_NoException() {
        RegistrationRequest request = new RegistrationRequest(
            "John Doe",
            "johndoe",
            "john@example.com",
            "123456"
        );
        
        assertDoesNotThrow(() -> validationService.validateRegistrationInput(request));
    }
    
    // Login validation tests
    
    @Test
    void testValidateLoginInput_ValidData_NoException() {
        LoginRequest request = new LoginRequest("johndoe", "password123");
        
        assertDoesNotThrow(() -> validationService.validateLoginInput(request));
    }
    
    @Test
    void testValidateLoginInput_NullRequest_ThrowsException() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validationService.validateLoginInput(null)
        );
        assertEquals("Login request cannot be null", exception.getMessage());
    }
    
    @Test
    void testValidateLoginInput_EmptyUsername_ThrowsException() {
        LoginRequest request = new LoginRequest("", "password123");
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validationService.validateLoginInput(request)
        );
        assertEquals("Username is required", exception.getMessage());
    }
    
    @Test
    void testValidateLoginInput_WhitespaceUsername_ThrowsException() {
        LoginRequest request = new LoginRequest("   ", "password123");
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validationService.validateLoginInput(request)
        );
        assertEquals("Username is required", exception.getMessage());
    }
    
    @Test
    void testValidateLoginInput_EmptyPassword_ThrowsException() {
        LoginRequest request = new LoginRequest("johndoe", "");
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validationService.validateLoginInput(request)
        );
        assertEquals("Password is required", exception.getMessage());
    }
    
    @Test
    void testValidateLoginInput_WhitespacePassword_ThrowsException() {
        LoginRequest request = new LoginRequest("johndoe", "   ");
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> validationService.validateLoginInput(request)
        );
        assertEquals("Password is required", exception.getMessage());
    }
    
    // Email format validation tests
    
    @Test
    void testValidateEmailFormat_ValidEmail_ReturnsTrue() {
        assertTrue(validationService.validateEmailFormat("user@example.com"));
        assertTrue(validationService.validateEmailFormat("test.user@domain.co.uk"));
        assertTrue(validationService.validateEmailFormat("user+tag@example.com"));
    }
    
    @Test
    void testValidateEmailFormat_InvalidEmail_ReturnsFalse() {
        assertFalse(validationService.validateEmailFormat("notanemail"));
        assertFalse(validationService.validateEmailFormat("missing@domain"));
        assertFalse(validationService.validateEmailFormat("@example.com"));
        assertFalse(validationService.validateEmailFormat("user@"));
        assertFalse(validationService.validateEmailFormat(""));
        assertFalse(validationService.validateEmailFormat(null));
    }
    
    @Test
    void testValidateEmailFormat_EmailWithWhitespace_HandlesCorrectly() {
        assertTrue(validationService.validateEmailFormat("  user@example.com  "));
        assertFalse(validationService.validateEmailFormat("   "));
    }
}
