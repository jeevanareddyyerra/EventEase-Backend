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
    @DisplayName("initializeAdmin: skips when ADMIN_EMAIL is null or empty")
    void testInitializeAdmin_SkipsWhenEmailEmpty() {
        adminInitializer.setAdminEmail(null);
        adminInitializer.setAdminPassword("Secret123");
        assertFalse(adminInitializer.initializeAdmin());

        adminInitializer.setAdminEmail("   ");
        assertFalse(adminInitializer.initializeAdmin());

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("initializeAdmin: skips when ADMIN_PASSWORD is null or empty")
    void testInitializeAdmin_SkipsWhenPasswordEmpty() {
        adminInitializer.setAdminEmail("admin@eventease.com");
        adminInitializer.setAdminPassword(null);
        assertFalse(adminInitializer.initializeAdmin());

        adminInitializer.setAdminPassword("   ");
        assertFalse(adminInitializer.initializeAdmin());

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("initializeAdmin: rejects password with less than 6 characters")
    void testInitializeAdmin_RejectsShortPassword() {
        adminInitializer.setAdminEmail("admin@eventease.com");
        adminInitializer.setAdminPassword("12345"); // 5 chars

        assertFalse(adminInitializer.initializeAdmin());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("initializeAdmin: creates new ADMIN user when account does not exist")
    void testInitializeAdmin_CreatesNewAdminUser() {
        adminInitializer.setAdminEmail("  Admin@EventEase.COM  ");
        adminInitializer.setAdminPassword("SecurePassword123!");
        adminInitializer.setAdminName("Chief Administrator");

        when(userRepository.findByEmail("admin@eventease.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("SecurePassword123!")).thenReturn("hashed-bcrypt-secret");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        boolean created = adminInitializer.initializeAdmin();

        assertTrue(created);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals("admin@eventease.com", savedUser.getEmail());
        assertEquals("Chief Administrator", savedUser.getName());
        assertEquals(Role.ADMIN, savedUser.getRole());
        assertEquals(OrganizerStatus.APPROVED, savedUser.getOrganizerStatus());
        assertEquals("hashed-bcrypt-secret", savedUser.getPassword());
        assertNotNull(savedUser.getCreatedAt());
    }

    @Test
    @DisplayName("initializeAdmin: promotes existing user to ADMIN and updates credentials")
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
    @DisplayName("initializeAdmin: default name is Platform Administrator when name omitted")
    void testInitializeAdmin_DefaultNameWhenOmitted() {
        adminInitializer.setAdminEmail("admin@eventease.com");
        adminInitializer.setAdminPassword("Password123!");
        adminInitializer.setAdminName(null);

        when(userRepository.findByEmail("admin@eventease.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password123!")).thenReturn("hashed-pwd");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        assertTrue(adminInitializer.initializeAdmin());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("Platform Administrator", captor.getValue().getName());
    }

    @Test
    @DisplayName("run: executes initialization on application startup")
    void testRun_ExecutesInitialization() {
        adminInitializer.setAdminEmail(null);
        adminInitializer.run(null);
        verify(userRepository, never()).save(any());
    }
}
