package com.eventease.controller;

import com.eventease.dto.request.LoginRequest;
import com.eventease.dto.request.SignupRequest;
import com.eventease.dto.response.AuthResponse;
import com.eventease.entity.OrganizerStatus;
import com.eventease.entity.Role;
import com.eventease.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    @DisplayName("login: returns 200 OK with AuthResponse when credentials are valid")
    void login_returnsAuthResponse() {
        LoginRequest request = new LoginRequest("admin@eventease.io", "Admin@12345");
        AuthResponse authResponse = new AuthResponse(
                "mock.jwt.token",
                1L,
                "Platform Administrator",
                "admin@eventease.io",
                Role.ADMIN,
                OrganizerStatus.APPROVED
        );

        when(authService.login(request)).thenReturn(authResponse);

        ResponseEntity<AuthResponse> response = authController.login(request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("admin@eventease.io", response.getBody().getEmail());
        assertEquals(Role.ADMIN, response.getBody().getRole());
        assertEquals(OrganizerStatus.APPROVED, response.getBody().getOrganizerStatus());
        assertEquals("mock.jwt.token", response.getBody().getToken());
        verify(authService).login(request);
    }

    @Test
    @DisplayName("signup: returns 201 CREATED with AuthResponse")
    void signup_returnsCreated() {
        SignupRequest request = new SignupRequest("New User", "user@test.com", "Secret123", "1234567890", "PARTICIPANT");
        AuthResponse authResponse = new AuthResponse(
                "user.jwt.token",
                2L,
                "New User",
                "user@test.com",
                Role.USER,
                OrganizerStatus.NONE
        );

        when(authService.signup(request)).thenReturn(authResponse);

        ResponseEntity<AuthResponse> response = authController.signup(request);

        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(Role.USER, response.getBody().getRole());
        assertEquals("user.jwt.token", response.getBody().getToken());
        verify(authService).signup(request);
    }
}
