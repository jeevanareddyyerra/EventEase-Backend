package com.eventease.controller;

import com.eventease.dto.request.AiChatRequest;
import com.eventease.dto.response.AiChatResponse;
import com.eventease.entity.DocumentType;
import com.eventease.entity.Event;
import com.eventease.entity.EventStatus;
import com.eventease.exception.GlobalExceptionHandler;
import com.eventease.exception.ResourceNotFoundException;
import com.eventease.rag.assistant.AiAssistantService;
import com.eventease.rag.assistant.CitationSource;
import com.eventease.rag.assistant.GroundedAnswerResponse;
import com.eventease.service.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AiAssistantControllerTest {

    @Mock
    private AiAssistantService aiAssistantService;

    @Mock
    private EventService eventService;

    @InjectMocks
    private AiAssistantController aiAssistantController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Event publishedEvent;
    private Event pendingEvent;
    private CitationSource sampleCitation;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(aiAssistantController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        publishedEvent = new Event();
        publishedEvent.setId(10L);
        publishedEvent.setTitle("Hackathon 2026");
        publishedEvent.setStatus(EventStatus.PUBLISHED);

        pendingEvent = new Event();
        pendingEvent.setId(20L);
        pendingEvent.setTitle("Draft Unapproved Event");
        pendingEvent.setStatus(EventStatus.PENDING);

        sampleCitation = new CitationSource(
                10L,
                "Hackathon 2026",
                1L,
                "Official Rules",
                DocumentType.RULES,
                "event-10-doc-1-chunk-0",
                0.89
        );
    }

    @Test
    @DisplayName("Should return 200 OK with grounded answer and citations for valid question")
    void shouldReturnGroundedAnswerForValidQuestion() throws Exception {
        GroundedAnswerResponse mockAnswer = GroundedAnswerResponse.grounded(
                "Teams may have up to 4 members.",
                List.of(sampleCitation),
                0.89,
                "gemini-3.8-flash"
        );

        when(aiAssistantService.answerQuestion(eq("What is the team size?"), isNull(), isNull()))
                .thenReturn(mockAnswer);

        AiChatRequest request = new AiChatRequest("What is the team size?");

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", is("Teams may have up to 4 members.")))
                .andExpect(jsonPath("$.grounded", is(true)))
                .andExpect(jsonPath("$.evidenceFound", is(true)))
                .andExpect(jsonPath("$.topSimilarityScore", closeTo(0.89, 0.001)))
                .andExpect(jsonPath("$.modelUsed", is("gemini-3.8-flash")))
                .andExpect(jsonPath("$.fallbackReason").doesNotExist())
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].eventId", is(10)))
                .andExpect(jsonPath("$.sources[0].eventTitle", is("Hackathon 2026")))
                .andExpect(jsonPath("$.sources[0].documentTitle", is("Official Rules")))
                .andExpect(jsonPath("$.sources[0].documentType", is("RULES")))
                .andExpect(jsonPath("$.sources[0].chunkId", is("event-10-doc-1-chunk-0")))
                .andExpect(jsonPath("$.sources[0].similarityScore", closeTo(0.89, 0.001)));

        verify(aiAssistantService).answerQuestion("What is the team size?", null, null);
        verifyNoInteractions(eventService);
    }

    @Test
    @DisplayName("Should validate event publication and pass eventId and documentType to service")
    void shouldFilterByEventIdAndDocumentType() throws Exception {
        when(eventService.getEventById(10L)).thenReturn(publishedEvent);

        GroundedAnswerResponse mockAnswer = GroundedAnswerResponse.grounded(
                "Laptops and chargers are required.",
                List.of(sampleCitation),
                0.92,
                "gemini-3.8-flash"
        );

        when(aiAssistantService.answerQuestion(eq("What equipment is required?"), eq(10L), eq(DocumentType.RULES)))
                .thenReturn(mockAnswer);

        AiChatRequest request = new AiChatRequest("What equipment is required?", 10L, DocumentType.RULES);

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", is("Laptops and chargers are required.")))
                .andExpect(jsonPath("$.grounded", is(true)))
                .andExpect(jsonPath("$.sources", hasSize(1)));

        verify(eventService).getEventById(10L);
        verify(aiAssistantService).answerQuestion("What equipment is required?", 10L, DocumentType.RULES);
    }

    @Test
    @DisplayName("Should return 400 Bad Request when question is blank")
    void shouldRejectBlankQuestion() throws Exception {
        String jsonPayload = """
                {
                    "question": "   "
                }
                """;

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Validation failed")))
                .andExpect(jsonPath("$.validationErrors.question", notNullValue()));

        verifyNoInteractions(aiAssistantService);
        verifyNoInteractions(eventService);
    }

    @Test
    @DisplayName("Should return 400 Bad Request when question exceeds maximum length")
    void shouldRejectOversizedQuestion() throws Exception {
        String longQuestion = "a".repeat(1001);
        AiChatRequest request = new AiChatRequest(longQuestion);

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Validation failed")))
                .andExpect(jsonPath("$.validationErrors.question", notNullValue()));

        verifyNoInteractions(aiAssistantService);
    }

    @Test
    @DisplayName("Should return 400 Bad Request when documentType is invalid enum value")
    void shouldRejectInvalidDocumentType() throws Exception {
        String jsonPayload = """
                {
                    "question": "What is the schedule?",
                    "documentType": "INVALID_TYPE_NAME"
                }
                """;

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Malformed JSON request or invalid input value format")));

        verifyNoInteractions(aiAssistantService);
    }

    @Test
    @DisplayName("Should return 400 Bad Request when eventId is malformed string")
    void shouldRejectMalformedEventId() throws Exception {
        String jsonPayload = """
                {
                    "question": "Where is the venue?",
                    "eventId": "not-a-number"
                }
                """;

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Malformed JSON request or invalid input value format")));

        verifyNoInteractions(aiAssistantService);
    }

    @Test
    @DisplayName("Should return 400 Bad Request when eventId is negative or zero")
    void shouldRejectNegativeEventId() throws Exception {
        AiChatRequest request = new AiChatRequest("Valid question?", -5L);

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.validationErrors.eventId", containsString("positive")));

        verifyNoInteractions(aiAssistantService);
    }

    @Test
    @DisplayName("Should return 404 Not Found when eventId does not exist")
    void shouldReturn404WhenEventDoesNotExist() throws Exception {
        when(eventService.getEventById(999L))
                .thenThrow(new ResourceNotFoundException("Event not found with id: 999"));

        AiChatRequest request = new AiChatRequest("Tell me about this event", 999L);

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("Event not found with id: 999")));

        verify(eventService).getEventById(999L);
        verifyNoInteractions(aiAssistantService);
    }

    @Test
    @DisplayName("Should enforce public visibility: reject questions for non-published events with 400 Bad Request")
    void shouldEnforcePublicVisibilityForUnpublishedEvents() throws Exception {
        when(eventService.getEventById(20L)).thenReturn(pendingEvent);

        AiChatRequest request = new AiChatRequest("Tell me about this draft event", 20L);

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Questions can only be asked for PUBLISHED events")));

        verify(eventService).getEventById(20L);
        verifyNoInteractions(aiAssistantService);
    }

    @Test
    @DisplayName("Should return calibrated fallback when evidence is insufficient")
    void shouldHandleInsufficientEvidenceGracefully() throws Exception {
        GroundedAnswerResponse fallbackResponse = GroundedAnswerResponse.insufficientEvidence(0.15);
        when(aiAssistantService.answerQuestion(eq("What is the grand prize?"), isNull(), isNull()))
                .thenReturn(fallbackResponse);

        AiChatRequest request = new AiChatRequest("What is the grand prize?");

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded", is(false)))
                .andExpect(jsonPath("$.evidenceFound", is(false)))
                .andExpect(jsonPath("$.fallbackReason", is("INSUFFICIENT_EVIDENCE")))
                .andExpect(jsonPath("$.sources", empty()))
                .andExpect(jsonPath("$.answer", containsString("do not have sufficient information")));
    }

    @Test
    @DisplayName("Should return citation summary fallback when AI model is unconfigured")
    void shouldHandleUnconfiguredAiModelGracefully() throws Exception {
        GroundedAnswerResponse fallbackResponse = GroundedAnswerResponse.fallback(
                "AI answer generation is currently unavailable. Relevant documentation was found in: \"Official Rules\".",
                List.of(sampleCitation),
                0.85,
                "AI_CLIENT_UNCONFIGURED"
        );

        when(aiAssistantService.answerQuestion(eq("What are the rules?"), isNull(), isNull()))
                .thenReturn(fallbackResponse);

        AiChatRequest request = new AiChatRequest("What are the rules?");

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded", is(false)))
                .andExpect(jsonPath("$.evidenceFound", is(true)))
                .andExpect(jsonPath("$.fallbackReason", is("AI_CLIENT_UNCONFIGURED")))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.answer", containsString("AI answer generation is currently unavailable")));
    }

    @Test
    @DisplayName("Should return citation summary fallback when AI model call fails")
    void shouldHandleAiModelFailureGracefully() throws Exception {
        GroundedAnswerResponse fallbackResponse = GroundedAnswerResponse.fallback(
                "The AI assistant is temporarily unavailable. Relevant documentation was found in: \"Official Rules\".",
                List.of(sampleCitation),
                0.88,
                "AI_CLIENT_ERROR"
        );

        when(aiAssistantService.answerQuestion(eq("What are the rules?"), isNull(), isNull()))
                .thenReturn(fallbackResponse);

        AiChatRequest request = new AiChatRequest("What are the rules?");

        mockMvc.perform(post("/api/ai/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded", is(false)))
                .andExpect(jsonPath("$.evidenceFound", is(true)))
                .andExpect(jsonPath("$.fallbackReason", is("AI_CLIENT_ERROR")))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.answer", containsString("The AI assistant is temporarily unavailable")));
    }
}
