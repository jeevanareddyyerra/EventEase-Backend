package com.eventease.controller;

import com.eventease.dto.request.AiChatRequest;
import com.eventease.entity.Role;
import com.eventease.entity.User;
import com.eventease.rag.assistant.AiAssistantService;
import com.eventease.rag.assistant.GroundedAnswerResponse;
import com.eventease.repository.UserRepository;
import com.eventease.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AiAssistantSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AiAssistantService aiAssistantService;

    @MockitoBean
    private UserRepository userRepository;

    private String userToken;
    private String organizerToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        User regularUser = new User();
        regularUser.setId(101L);
        regularUser.setName("User One");
        regularUser.setEmail("user1@example.com");
        regularUser.setPassword("password123");
        regularUser.setRole(Role.USER);

        User organizerUser = new User();
        organizerUser.setId(102L);
        organizerUser.setName("Organizer One");
        organizerUser.setEmail("organizer1@example.com");
        organizerUser.setPassword("password123");
        organizerUser.setRole(Role.ORGANIZER);

        User adminUser = new User();
        adminUser.setId(103L);
        adminUser.setName("Admin One");
        adminUser.setEmail("admin1@example.com");
        adminUser.setPassword("password123");
        adminUser.setRole(Role.ADMIN);

        when(userRepository.findByEmail("user1@example.com")).thenReturn(Optional.of(regularUser));
        when(userRepository.findByEmail("organizer1@example.com")).thenReturn(Optional.of(organizerUser));
        when(userRepository.findByEmail("admin1@example.com")).thenReturn(Optional.of(adminUser));

        userToken = jwtService.generateToken("user1@example.com");
        organizerToken = jwtService.generateToken("organizer1@example.com");
        adminToken = jwtService.generateToken("admin1@example.com");

        GroundedAnswerResponse defaultAnswer = GroundedAnswerResponse.grounded(
                "Yes, the event is open to all students.",
                Collections.emptyList(),
                0.88,
                "gemini-3.8-flash"
        );
        when(aiAssistantService.answerQuestion(any(), any(), any())).thenReturn(defaultAnswer);
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when unauthenticated request calls AI Assistant")
    void shouldRejectUnauthenticatedRequest() throws Exception {
        AiChatRequest request = new AiChatRequest("Is attendance mandatory?");

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", containsString("Authentication required")));
    }

    @Test
    @DisplayName("Should allow authenticated USER role to access AI Assistant")
    void shouldAllowUserRole() throws Exception {
        AiChatRequest request = new AiChatRequest("What is the registration process?");

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded", is(true)))
                .andExpect(jsonPath("$.answer", notNullValue()));
    }

    @Test
    @DisplayName("Should allow authenticated ORGANIZER role to access AI Assistant")
    void shouldAllowOrganizerRole() throws Exception {
        AiChatRequest request = new AiChatRequest("Where can guidelines be found?");

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded", is(true)));
    }

    @Test
    @DisplayName("Should allow authenticated ADMIN role to access AI Assistant")
    void shouldAllowAdminRole() throws Exception {
        AiChatRequest request = new AiChatRequest("What events are published?");

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded", is(true)));
    }

    @Test
    @DisplayName("Should ensure existing endpoint security rules remain untouched")
    void shouldVerifyExistingEndpointSecurityUnchanged() throws Exception {
        // Unauthenticated access to events is rejected
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isUnauthorized());

        // Unauthenticated access to admin endpoints is rejected
        mockMvc.perform(get("/api/admin/summary"))
                .andExpect(status().isUnauthorized());

        // Regular USER role cannot access admin endpoints (403 Forbidden)
        mockMvc.perform(get("/api/admin/summary")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.message", containsString("Access denied")));
    }
}
