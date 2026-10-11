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
 * - If ADMIN_EMAIL or ADMIN_PASSWORD is omitted, initialization safely skips.
 */
@Component
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${eventease.admin.email:}")
    private String adminEmail;

    @Value("${eventease.admin.password:}")
    private String adminPassword;

    @Value("${eventease.admin.name:Platform Administrator}")
    private String adminName;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
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
        if (adminEmail == null || adminEmail.isBlank()) {
            log.info("Admin initialization skipped: ADMIN_EMAIL environment variable is not configured.");
            return false;
        }

        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("Admin initialization skipped: ADMIN_PASSWORD environment variable is not configured.");
            return false;
        }

        if (adminPassword.length() < 6) {
            log.error("Admin initialization failed: ADMIN_PASSWORD must be at least 6 characters long.");
            return false;
        }

        String normalizedEmail = adminEmail.trim().toLowerCase();
        String displayName = (adminName != null && !adminName.isBlank()) ? adminName.trim() : "Platform Administrator";

        Optional<User> existingUserOpt = userRepository.findByEmail(normalizedEmail);

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
            existing.setPassword(passwordEncoder.encode(adminPassword));
            needsUpdate = true;

            if (needsUpdate) {
                userRepository.save(existing);
                log.info("Existing user [{}] successfully promoted and synchronized as ADMIN.", normalizedEmail);
            }
            return true;
        }

        User newAdmin = new User();
        newAdmin.setName(displayName);
        newAdmin.setEmail(normalizedEmail);
        newAdmin.setPassword(passwordEncoder.encode(adminPassword));
        newAdmin.setRole(Role.ADMIN);
        newAdmin.setOrganizerStatus(OrganizerStatus.APPROVED);
        newAdmin.setCreatedAt(LocalDateTime.now());

        userRepository.save(newAdmin);
        log.info("Platform administrator account [{}] initialized successfully.", normalizedEmail);
        return true;
    }
}
