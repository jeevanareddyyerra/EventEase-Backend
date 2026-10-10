package com.eventease.controller;

import com.eventease.entity.Role;
import com.eventease.entity.User;
import com.eventease.repository.UserRepository;
import com.eventease.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ActuatorSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private String userToken;

    @BeforeEach
    void setUp() {
        User regularUser = new User();
        regularUser.setId(301L);
        regularUser.setName("Actuator Test User");
        regularUser.setEmail("actuator_user@example.com");
        regularUser.setPassword("password123");
        regularUser.setRole(Role.USER);

        when(userRepository.findByEmail("actuator_user@example.com")).thenReturn(Optional.of(regularUser));
        userToken = jwtService.generateToken("actuator_user@example.com");
    }

    @Test
    @DisplayName("Should allow unauthenticated access to /actuator/health returning UP status without sensitive details")
    void shouldAllowUnauthenticatedAccessToHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @DisplayName("Should allow unauthenticated access to /actuator/info returning application metadata")
    void shouldAllowUnauthenticatedAccessToInfoEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/info")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.app.name", is("EventEase")))
                .andExpect(jsonPath("$.app.version", is("0.0.1-SNAPSHOT")))
                .andExpect(jsonPath("$.app.description", notNullValue()));
    }

    @Test
    @DisplayName("Should reject unauthenticated access to sensitive actuator endpoints like /actuator/env")
    void shouldRejectUnauthenticatedAccessToSensitiveEnvEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("Should reject unauthenticated access to sensitive actuator endpoints like /actuator/beans")
    void shouldRejectUnauthenticatedAccessToSensitiveBeansEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("Should ensure application endpoints remain protected from unauthenticated access")
    void shouldRejectUnauthenticatedAccessToApplicationEndpoints() throws Exception {
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));

        mockMvc.perform(get("/api/admin/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("Should ensure role-based authorization is enforced for application endpoints")
    void shouldEnforceRoleAuthorizationOnAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/summary")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }
}
