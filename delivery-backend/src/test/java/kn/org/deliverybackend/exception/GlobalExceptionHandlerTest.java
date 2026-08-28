//package kn.org.deliverybackend.exception;
//
//import kn.org.deliverybackend.dto.response.auth.LoginResponse;
//import kn.org.deliverybackend.dto.response.auth.RegistrationResponse;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.context.request.WebRequest;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.Mockito.*;
//
///**
// * Unit tests for GlobalExceptionHandler.
// * Tests error handling for validation errors (400), conflicts (400),
// * authentication failures (401), and server errors (500).
// */
//class GlobalExceptionHandlerTest {
//
//    private GlobalExceptionHandler exceptionHandler;
//    private WebRequest webRequest;
//
//    @BeforeEach
//    void setUp() {
//        exceptionHandler = new GlobalExceptionHandler();
//        webRequest = mock(WebRequest.class);
//    }
//
//    @Test
//    void testHandleValidationError_Register() {
//        // Arrange
//        IllegalArgumentException ex = new IllegalArgumentException("Username is required");
//        when(webRequest.getDescription(false)).thenReturn("uri=/api/auth/register");
//
//        // Act
//        ResponseEntity<?> response = exceptionHandler.handleIllegalArgumentException(ex, webRequest);
//
//        // Assert
//        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
//        assertNotNull(response.getBody());
//        assertTrue(response.getBody() instanceof RegistrationResponse);
//
//        RegistrationResponse body = (RegistrationResponse) response.getBody();
//        assertFalse(body.isSuccess());
//        assertEquals("Username is required", body.getError());
//    }
//
//    @Test
//    void testHandleConflictError_UsernameAlreadyTaken() {
//        // Arrange
//        IllegalArgumentException ex = new IllegalArgumentException("Username already taken");
//        when(webRequest.getDescription(false)).thenReturn("uri=/api/auth/register");
//
//        // Act
//        ResponseEntity<?> response = exceptionHandler.handleIllegalArgumentException(ex, webRequest);
//
//        // Assert
//        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
//        assertNotNull(response.getBody());
//        assertTrue(response.getBody() instanceof RegistrationResponse);
//
//        RegistrationResponse body = (RegistrationResponse) response.getBody();
//        assertFalse(body.isSuccess());
//        assertEquals("Username already taken", body.getError());
//    }
//
//    @Test
//    void testHandleConflictError_EmailAlreadyRegistered() {
//        // Arrange
//        IllegalArgumentException ex = new IllegalArgumentException("Email already registered");
//        when(webRequest.getDescription(false)).thenReturn("uri=/api/auth/register");
//
//        // Act
//        ResponseEntity<?> response = exceptionHandler.handleIllegalArgumentException(ex, webRequest);
//
//        // Assert
//        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
//        assertNotNull(response.getBody());
//        assertTrue(response.getBody() instanceof RegistrationResponse);
//
//        RegistrationResponse body = (RegistrationResponse) response.getBody();
//        assertFalse(body.isSuccess());
//        assertEquals("Email already registered", body.getError());
//    }
//
//    @Test
//    void testHandleAuthenticationError_InvalidCredentials() {
//        // Arrange
//        IllegalArgumentException ex = new IllegalArgumentException("Invalid credentials");
//        when(webRequest.getDescription(false)).thenReturn("uri=/api/auth/login");
//
//        // Act
//        ResponseEntity<?> response = exceptionHandler.handleIllegalArgumentException(ex, webRequest);
//
//        // Assert
//        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
//        assertNotNull(response.getBody());
//        assertTrue(response.getBody() instanceof LoginResponse);
//
//        LoginResponse body = (LoginResponse) response.getBody();
//        assertFalse(body.isSuccess());
//        assertEquals("Invalid credentials", body.getError());
//    }
//
//    @Test
//    void testHandleValidationError_Login() {
//        // Arrange
//        IllegalArgumentException ex = new IllegalArgumentException("Password is required");
//        when(webRequest.getDescription(false)).thenReturn("uri=/api/auth/login");
//
//        // Act
//        ResponseEntity<?> response = exceptionHandler.handleIllegalArgumentException(ex, webRequest);
//
//        // Assert
//        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
//        assertNotNull(response.getBody());
//        assertTrue(response.getBody() instanceof LoginResponse);
//
//        LoginResponse body = (LoginResponse) response.getBody();
//        assertFalse(body.isSuccess());
//        assertEquals("Password is required", body.getError());
//    }
//
//    @Test
//    void testHandleServerError() {
//        // Arrange
//        Exception ex = new RuntimeException("Database connection failed");
//        when(webRequest.getDescription(false)).thenReturn("uri=/api/auth/register");
//
//        // Act
//        ResponseEntity<?> response = exceptionHandler.handleGlobalException(ex, webRequest);
//
//        // Assert
//        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
//        assertNotNull(response.getBody());
//        assertTrue(response.getBody() instanceof RegistrationResponse);
//
//        RegistrationResponse body = (RegistrationResponse) response.getBody();
//        assertFalse(body.isSuccess());
//        assertEquals("Internal server error", body.getError());
//    }
//
//    @Test
//    void testHandleServerError_Login() {
//        // Arrange
//        Exception ex = new NullPointerException("Unexpected null value");
//        when(webRequest.getDescription(false)).thenReturn("uri=/api/auth/login");
//
//        // Act
//        ResponseEntity<?> response = exceptionHandler.handleGlobalException(ex, webRequest);
//
//        // Assert
//        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
//        assertNotNull(response.getBody());
//        assertTrue(response.getBody() instanceof LoginResponse);
//
//        LoginResponse body = (LoginResponse) response.getBody();
//        assertFalse(body.isSuccess());
//        assertEquals("Internal server error", body.getError());
//    }
//}
