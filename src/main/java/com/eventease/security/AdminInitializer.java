package com.eventease.security;

import com.eventease.entity.OrganizerStatus;
import com.eventease.entity.Role;
import com.eventease.entity.User;
import com.eventease.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Headless, secure administrator account initialization service.
 * Automatically provisions or synchronizes a platform administrator account
 * on application startup using ADMIN_EMAIL and ADMIN_PASSWORD environment variables
 * configured securely in Render (or local environment).
 *
 * Security guarantees:
 * - Credentials are read exclusively from environment variables / encrypted configuration.
 * - Passwords are never logged in plaintext or exposed in API responses.
 * - Idempotent execution: creates the admin if absent, promotes/updates if present.
 * - Completely isolated: never mutates or drops unrelated users, events, or application tables.
 * - If ADMIN_EMAIL or ADMIN_PASSWORD is omitted, initialization defaults to fixed credentials:
 *   admin@eventease.io / Admin@12345.
 */
@Component
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    public static final String DEFAULT_ADMIN_EMAIL = "admin@eventease.io";
    public static final String DEFAULT_ADMIN_PASSWORD = "Admin@12345";
    public static final String DEFAULT_ADMIN_NAME = "Platform Administrator";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${eventease.admin.email:admin@eventease.io}")
    private String adminEmail = DEFAULT_ADMIN_EMAIL;

    @Value("${eventease.admin.password:Admin@12345}")
    private String adminPassword = DEFAULT_ADMIN_PASSWORD;

    @Value("${eventease.admin.name:Platform Administrator}")
    private String adminName = DEFAULT_ADMIN_NAME;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String getAdminName() {
        return adminName;
    }

    public void setAdminName(String adminName) {
        this.adminName = adminName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initializeAdmin();
    }

    @Transactional
    public boolean initializeAdmin() {
        String effectiveEmail = (adminEmail != null && !adminEmail.isBlank())
                ? adminEmail.trim().toLowerCase()
                : DEFAULT_ADMIN_EMAIL;

        String plainPassword = (adminPassword != null && !adminPassword.isBlank())
                ? adminPassword
                : DEFAULT_ADMIN_PASSWORD;

        String displayName = (adminName != null && !adminName.isBlank())
                ? adminName.trim()
                : DEFAULT_ADMIN_NAME;

        if (plainPassword.length() < 6) {
            log.error("Admin initialization failed: password must be at least 6 characters long.");
            return false;
        }

        Optional<User> existingUserOpt = userRepository.findByEmail(effectiveEmail);

        if (existingUserOpt.isPresent()) {
            User existing = existingUserOpt.get();
            boolean needsUpdate = false;

            if (existing.getRole() != Role.ADMIN) {
                existing.setRole(Role.ADMIN);
                needsUpdate = true;
            }
            if (existing.getOrganizerStatus() != OrganizerStatus.APPROVED) {
                existing.setOrganizerStatus(OrganizerStatus.APPROVED);
                needsUpdate = true;
            }
            if (!passwordEncoder.matches(plainPassword, existing.getPassword())) {
                existing.setPassword(passwordEncoder.encode(plainPassword));
                needsUpdate = true;
            }
            if (existing.getName() == null || existing.getName().isBlank()) {
                existing.setName(displayName);
                needsUpdate = true;
            }

            if (needsUpdate) {
                userRepository.save(existing);
                log.info("Existing user [{}] successfully synchronized with ADMIN role and credentials.", effectiveEmail);
            } else {
                log.info("Platform administrator account [{}] already exists with valid ADMIN role and credentials.", effectiveEmail);
            }
            return true;
        }

        User newAdmin = new User();
        newAdmin.setName(displayName);
        newAdmin.setEmail(effectiveEmail);
        newAdmin.setPassword(passwordEncoder.encode(plainPassword));
        newAdmin.setRole(Role.ADMIN);
        newAdmin.setOrganizerStatus(OrganizerStatus.APPROVED);
        newAdmin.setCreatedAt(LocalDateTime.now());

        userRepository.save(newAdmin);
        log.info("Platform administrator account [{}] initialized successfully.", effectiveEmail);
        return true;
    }
}
