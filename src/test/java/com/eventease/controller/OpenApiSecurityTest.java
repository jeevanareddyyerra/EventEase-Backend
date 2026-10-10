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
class OpenApiSecurityTest {

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
        regularUser.setId(201L);
        regularUser.setName("Regular User");
        regularUser.setEmail("openapi_user@example.com");
        regularUser.setPassword("password123");
        regularUser.setRole(Role.USER);

        when(userRepository.findByEmail("openapi_user@example.com")).thenReturn(Optional.of(regularUser));
        userToken = jwtService.generateToken("openapi_user@example.com");
    }

    @Test
    @DisplayName("Should serve OpenAPI v3 JSON specification without authentication")
    void shouldServeOpenApiDocsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.openapi", startsWith("3.")))
                .andExpect(jsonPath("$.info.title", is("EventEase API")))
                .andExpect(jsonPath("$.info.version", is("0.0.1-SNAPSHOT")))
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth", notNullValue()))
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.type", is("http")))
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.scheme", is("bearer")))
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.bearerFormat", is("JWT")));
    }

    @Test
    @DisplayName("Should serve Swagger UI HTML page without authentication")
    void shouldServeSwaggerUiIndexWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    @DisplayName("Should permit swagger-ui.html redirect without authentication")
    void shouldPermitSwaggerUiRedirectWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/swagger-ui/index.html")));
    }

    @Test
    @DisplayName("Should ensure protected application endpoints still require authentication")
    void shouldRejectUnauthenticatedAccessToProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));

        mockMvc.perform(get("/api/admin/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("Should ensure authorization rules are preserved for non-admin roles")
    void shouldRejectForbiddenAccessForUserRoleToAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/summary")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }
}
