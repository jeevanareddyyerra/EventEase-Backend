package com.eventease.service;

import com.eventease.dto.request.LoginRequest;
import com.eventease.dto.request.SignupRequest;
import com.eventease.dto.response.AuthResponse;
import com.eventease.entity.OrganizerStatus;
import com.eventease.entity.Role;
import com.eventease.entity.User;
import com.eventease.exception.DuplicateResourceException;
import com.eventease.repository.UserRepository;
import com.eventease.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Participant signup should assign role USER and organizerStatus NONE")
    void signup_participant_createsUserWithRoleUserAndStatusNone() {
        SignupRequest request = new SignupRequest("John Doe", "john@example.com", "password123", "1234567890", "PARTICIPANT");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(jwtService.generateToken("john@example.com")).thenReturn("jwt.token.here");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });

        AuthResponse response = authService.signup(request);

        assertNotNull(response);
        assertEquals(Role.USER, response.getRole());
        assertEquals(OrganizerStatus.NONE, response.getOrganizerStatus());
        assertEquals("jwt.token.here", response.getToken());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals(Role.USER, savedUser.getRole(), "Must never grant elevated role directly");
        assertEquals(OrganizerStatus.NONE, savedUser.getOrganizerStatus());
    }

    @Test
    @DisplayName("Organizer signup should assign role USER and organizerStatus PENDING")
    void signup_organizer_createsUserWithRoleUserAndStatusPending() {
        SignupRequest request = new SignupRequest("Jane Organizer", "jane@example.com", "password123", "9876543210", "ORGANIZER");

        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(jwtService.generateToken("jane@example.com")).thenReturn("jwt.token.organizer");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(2L);
            return u;
        });

        AuthResponse response = authService.signup(request);

        assertNotNull(response);
        assertEquals(Role.USER, response.getRole(), "New organizer must have role USER until approved");
        assertEquals(OrganizerStatus.PENDING, response.getOrganizerStatus(), "New organizer must have status PENDING");
        assertEquals("jwt.token.organizer", response.getToken());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals(Role.USER, savedUser.getRole());
        assertEquals(OrganizerStatus.PENDING, savedUser.getOrganizerStatus());
    }

    @Test
    @DisplayName("Signup with duplicate email throws DuplicateResourceException")
    void signup_duplicateEmail_throwsDuplicateResourceException() {
        SignupRequest request = new SignupRequest("Dup", "dup@example.com", "password123", "111");
        when(userRepository.findByEmail("dup@example.com")).thenReturn(Optional.of(new User()));

        assertThrows(DuplicateResourceException.class, () -> authService.signup(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Login returns AuthResponse with user's organizer status")
    void login_successful_returnsAuthResponseWithOrganizerStatus() {
        LoginRequest request = new LoginRequest("approved@example.com", "password123");
        User user = new User("Approved Org", "approved@example.com", "encoded", "123", Role.ORGANIZER);
        user.setId(5L);
        user.setOrganizerStatus(OrganizerStatus.APPROVED);

        when(userRepository.findByEmail("approved@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("approved@example.com")).thenReturn("jwt.token.approved");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals(Role.ORGANIZER, response.getRole());
        assertEquals(OrganizerStatus.APPROVED, response.getOrganizerStatus());
        assertEquals("jwt.token.approved", response.getToken());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }
}
