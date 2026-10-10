package com.eventease.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/test-endpoint");
    }

    @Test
    @DisplayName("Generic unhandled exception must be sanitized and never leak stack trace or internal messages")
    void shouldSanitizeGenericInternalServerError() {
        Exception rawException = new RuntimeException("CRITICAL: Database connection failed at jdbc:mysql://internal-host:3306/eventease with root pass 12345");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(rawException, request);

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(500, body.getStatus());
        assertEquals("An unexpected internal server error occurred", body.getMessage());
        assertEquals("/api/test-endpoint", body.getPath());
        assertNotNull(body.getTimestamp());

        // Verify sensitive message is not leaked in the client response
        assertFalse(body.getMessage().contains("Database connection failed"));
        assertFalse(body.getMessage().contains("root pass"));
    }

    @Test
    @DisplayName("ResourceNotFoundException should return 404 with resource message and path")
    void shouldHandleResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Event not found with id: 42");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleResourceNotFoundException(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(404, body.getStatus());
        assertEquals("Event not found with id: 42", body.getMessage());
        assertEquals("/api/test-endpoint", body.getPath());
    }

    @Test
    @DisplayName("DuplicateResourceException should return 409 Conflict")
    void shouldHandleDuplicateResourceException() {
        DuplicateResourceException ex = new DuplicateResourceException("User is already registered for this event.");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleDuplicateResourceException(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());

        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(409, body.getStatus());
        assertEquals("User is already registered for this event.", body.getMessage());
    }

    @Test
    @DisplayName("AuthenticationException should return 401 with generic message")
    void shouldHandleAuthenticationException() {
        org.springframework.security.core.AuthenticationException ex =
                new BadCredentialsException("Bad credentials for user");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAuthenticationException(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(401, body.getStatus());
        assertEquals("Invalid email or password", body.getMessage());
        assertFalse(body.getMessage().contains("for user"));
    }

    @Test
    @DisplayName("AccessDeniedException should return 403 Forbidden")
    void shouldHandleAccessDeniedException() {
        AccessDeniedException ex = new AccessDeniedException("Forbidden role access");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccessDeniedException(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());

        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(403, body.getStatus());
        assertEquals("Access denied", body.getMessage());
    }

    @Test
    @DisplayName("BusinessRuleException should preserve status and message")
    void shouldHandleBusinessRuleException() {
        BusinessRuleException ex = new BusinessRuleException("Participant capacity has been reached for this event.", HttpStatus.CONFLICT);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBusinessRuleException(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());

        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(409, body.getStatus());
        assertEquals("Participant capacity has been reached for this event.", body.getMessage());
    }
}
