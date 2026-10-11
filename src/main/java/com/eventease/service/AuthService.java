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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            log.warn("Signup attempt rejected: email already registered");
            throw new DuplicateResourceException("Email is already registered: " + request.getEmail().trim());
        }

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone() != null && !request.getPhone().isBlank() ? request.getPhone().trim() : null);
        user.setRole(Role.USER);
        if ("ORGANIZER".equalsIgnoreCase(request.getAccountType())) {
            user.setOrganizerStatus(OrganizerStatus.PENDING);
        } else {
            user.setOrganizerStatus(OrganizerStatus.NONE);
        }
        user.setCreatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);
        log.info("User registered successfully [userId={}, role={}, organizerStatus={}]",
                savedUser.getId(), savedUser.getRole(), savedUser.getOrganizerStatus());

        String token = jwtService.generateToken(savedUser.getEmail());

        return new AuthResponse(
                token,
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole(),
                savedUser.getOrganizerStatus()
        );
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
            );
        } catch (AuthenticationException e) {
            log.warn("Authentication failed for login attempt");
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    log.warn("Login attempt user not found after authentication");
                    return new BadCredentialsException("Invalid email or password");
                });

        log.info("User logged in successfully [userId={}, role={}, organizerStatus={}]",
                user.getId(), user.getRole(), user.getOrganizerStatus());
        String token = jwtService.generateToken(user.getEmail());

        return new AuthResponse(
                token,
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getOrganizerStatus()
        );
    }
}
