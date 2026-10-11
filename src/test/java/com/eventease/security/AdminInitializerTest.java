package com.eventease.security;

import com.eventease.entity.OrganizerStatus;
import com.eventease.entity.Role;
import com.eventease.entity.User;
import com.eventease.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminInitializer adminInitializer;

    @BeforeEach
    void setUp() {
        adminInitializer = new AdminInitializer(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("initializeAdmin: defaults to fixed credentials admin@eventease.io / Admin@12345 when omitted")
    void testInitializeAdmin_DefaultsToFixedCredentialsWhenOmitted() {
        adminInitializer.setAdminEmail(null);
        adminInitializer.setAdminPassword(null);
        adminInitializer.setAdminName(null);

        when(userRepository.findByEmail("admin@eventease.io")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Admin@12345")).thenReturn("bcrypt-Admin@12345");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        boolean created = adminInitializer.initializeAdmin();

        assertTrue(created);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User savedUser = captor.getValue();
        assertEquals("admin@eventease.io", savedUser.getEmail());
        assertEquals("Admin@12345", "Admin@12345");
        assertEquals("Platform Administrator", savedUser.getName());
        assertEquals(Role.ADMIN, savedUser.getRole());
        assertEquals(OrganizerStatus.APPROVED, savedUser.getOrganizerStatus());
        assertEquals("bcrypt-Admin@12345", savedUser.getPassword());
        assertNotNull(savedUser.getCreatedAt());
    }

    @Test
    @DisplayName("initializeAdmin: idempotent when admin already exists with matching credentials")
    void testInitializeAdmin_IdempotentWhenAdminAlreadyExists() {
        User existingAdmin = new User();
        existingAdmin.setId(1L);
        existingAdmin.setName("Platform Administrator");
        existingAdmin.setEmail("admin@eventease.io");
        existingAdmin.setPassword("$2a$10$existingHash");
        existingAdmin.setRole(Role.ADMIN);
        existingAdmin.setOrganizerStatus(OrganizerStatus.APPROVED);

        adminInitializer.setAdminEmail("admin@eventease.io");
        adminInitializer.setAdminPassword("Admin@12345");

        when(userRepository.findByEmail("admin@eventease.io")).thenReturn(Optional.of(existingAdmin));
        when(passwordEncoder.matches("Admin@12345", "$2a$10$existingHash")).thenReturn(true);

        boolean result = adminInitializer.initializeAdmin();

        assertTrue(result);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("initializeAdmin: rejects custom password with less than 6 characters")
    void testInitializeAdmin_RejectsShortPassword() {
        adminInitializer.setAdminEmail("admin@eventease.io");
        adminInitializer.setAdminPassword("12345"); // 5 chars

        assertFalse(adminInitializer.initializeAdmin());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("initializeAdmin: creates new ADMIN user when account does not exist")
    void testInitializeAdmin_CreatesNewAdminUser() {
        adminInitializer.setAdminEmail("  SuperAdmin@EventEase.IO  ");
        adminInitializer.setAdminPassword("SecurePassword123!");
        adminInitializer.setAdminName("Chief Administrator");

        when(userRepository.findByEmail("superadmin@eventease.io")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("SecurePassword123!")).thenReturn("hashed-bcrypt-secret");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        boolean created = adminInitializer.initializeAdmin();

        assertTrue(created);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals("superadmin@eventease.io", savedUser.getEmail());
        assertEquals("Chief Administrator", savedUser.getName());
        assertEquals(Role.ADMIN, savedUser.getRole());
        assertEquals(OrganizerStatus.APPROVED, savedUser.getOrganizerStatus());
        assertEquals("hashed-bcrypt-secret", savedUser.getPassword());
        assertNotNull(savedUser.getCreatedAt());
    }

    @Test
    @DisplayName("initializeAdmin: promotes existing user to ADMIN and synchronizes password if different")
    void testInitializeAdmin_PromotesExistingUser() {
        User existingUser = new User();
        existingUser.setId(42L);
        existingUser.setName("Existing Staff");
        existingUser.setEmail("staff@eventease.com");
        existingUser.setRole(Role.USER);
        existingUser.setOrganizerStatus(OrganizerStatus.NONE);
        existingUser.setPassword("old-hash");

        adminInitializer.setAdminEmail("staff@eventease.com");
        adminInitializer.setAdminPassword("NewAdminPassword123!");

        when(userRepository.findByEmail("staff@eventease.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("NewAdminPassword123!", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("NewAdminPassword123!")).thenReturn("new-bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        boolean updated = adminInitializer.initializeAdmin();

        assertTrue(updated);
        assertEquals(Role.ADMIN, existingUser.getRole());
        assertEquals(OrganizerStatus.APPROVED, existingUser.getOrganizerStatus());
        assertEquals("new-bcrypt-hash", existingUser.getPassword());
        verify(userRepository).save(existingUser);
    }

    @Test
    @DisplayName("run: executes initialization on application startup")
    void testRun_ExecutesInitialization() {
        when(userRepository.findByEmail("admin@eventease.io")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Admin@12345")).thenReturn("bcrypt-Admin@12345");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        adminInitializer.run(null);

        verify(userRepository).save(any(User.class));
    }
}
