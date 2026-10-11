package com.eventease.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CorsConfigurationTest {

    private static final String VERCEL_FRONTEND = "https://event-ease-frontend-chi.vercel.app";
    private static final String LOCALHOST_FRONTEND = "http://localhost:5173";
    private static final String UNTRUSTED_ORIGIN = "https://untrusted-attacker-site.com";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should successfully respond to CORS preflight for POST /api/auth/login from Vercel frontend")
    void shouldAllowPreflightRequestFromVercelFrontendToLoginEndpoint() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", VERCEL_FRONTEND)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type,Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", VERCEL_FRONTEND))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Methods", allOf(
                        containsString("POST"),
                        containsString("OPTIONS"),
                        containsString("GET"),
                        containsString("PUT"),
                        containsString("DELETE"),
                        containsString("PATCH")
                )))
                .andExpect(header().string("Access-Control-Allow-Headers", allOf(
                        containsString("Content-Type"),
                        containsString("Authorization")
                )));
    }

    @Test
    @DisplayName("Should successfully respond to CORS preflight for POST /api/auth/register from Vercel frontend")
    void shouldAllowPreflightRequestFromVercelFrontendToSignupEndpoint() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                        .header("Origin", VERCEL_FRONTEND)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", VERCEL_FRONTEND))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("Should allow preflight OPTIONS requests to protected endpoints without authentication")
    void shouldAllowPreflightRequestToProtectedEndpointWithoutAuthentication() throws Exception {
        mockMvc.perform(options("/api/events")
                        .header("Origin", VERCEL_FRONTEND)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", VERCEL_FRONTEND))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")));
    }

    @Test
    @DisplayName("Should attach Access-Control-Allow-Origin header to actual POST /api/auth/login request")
    void shouldIncludeCorsHeadersOnActualPostLoginRequest() throws Exception {
        // Even when login credentials fail (e.g. invalid user/password), CORS headers must be present
        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", VERCEL_FRONTEND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nonexistent@example.com\",\"password\":\"wrongpassword\"}"))
                .andExpect(header().string("Access-Control-Allow-Origin", VERCEL_FRONTEND))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("Should allow CORS preflight from local development origin http://localhost:5173")
    void shouldAllowLocalhostDevOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", LOCALHOST_FRONTEND)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", LOCALHOST_FRONTEND));
    }

    @Test
    @DisplayName("Should not include Access-Control-Allow-Origin for untrusted origins in production")
    void shouldRejectPreflightRequestFromUntrustedOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", UNTRUSTED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
