package com.eventease.rag.assistant;

import com.eventease.entity.DocumentType;
import com.eventease.entity.EventStatus;
import com.eventease.rag.client.AiModelClient;
import com.eventease.rag.client.AiModelResponse;
import com.eventease.rag.config.RagProperties;
import com.eventease.rag.model.DocumentChunk;
import com.eventease.rag.prompt.GroundedPromptBuilder;
import com.eventease.rag.retrieval.ScoredDocumentChunk;
import com.eventease.rag.retrieval.SemanticRetrievalResult;
import com.eventease.rag.retrieval.SemanticRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import com.eventease.entity.Event;
import com.eventease.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiAssistantServiceTest {

    @Mock
    private SemanticRetriever semanticRetriever;

    @Mock
    private AiModelClient aiModelClient;

    @Mock
    private EventRepository eventRepository;

    private GroundedPromptBuilder promptBuilder;
    private RagProperties ragProperties;
    private AiAssistantService assistantService;

    private DocumentChunk sampleChunk;

    @BeforeEach
    void setUp() {
        promptBuilder = new GroundedPromptBuilder();
        ragProperties = new RagProperties();
        assistantService = new AiAssistantService(semanticRetriever, promptBuilder, aiModelClient, ragProperties, eventRepository);

        sampleChunk = new DocumentChunk(
                "event-10-doc-1-chunk-0",
                10L,
                "Tech Summit 2026",
                EventStatus.PUBLISHED,
                1L,
                "Summit Rules",
                DocumentType.RULES,
                0,
                "Doors open at 8:30 AM. Badges are mandatory.",
                LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("Should generate grounded answer with citations when evidence and model succeed")
    void shouldGenerateGroundedAnswer() {
        ScoredDocumentChunk scored = new ScoredDocumentChunk(sampleChunk, 0.88);
        SemanticRetrievalResult retrievalResult = new SemanticRetrievalResult("what time do doors open?", List.of(scored), 5, 0.88);

        when(semanticRetriever.retrievePublicEvidence("what time do doors open?", null))
                .thenReturn(retrievalResult);
        when(aiModelClient.isConfigured()).thenReturn(true);
        when(aiModelClient.generateAnswer(anyString(), anyString()))
                .thenReturn(AiModelResponse.success("Doors open at 8:30 AM according to the summit rules.", "gemini-3.8-flash"));

        GroundedAnswerResponse response = assistantService.answerQuestion("what time do doors open?");

        assertTrue(response.isGrounded());
        assertTrue(response.isEvidenceFound());
        assertEquals("Doors open at 8:30 AM according to the summit rules.", response.getAnswer());
        assertEquals(0.88, response.getTopSimilarityScore(), 0.0001);
        assertEquals("gemini-3.8-flash", response.getModelUsed());
        assertNull(response.getFallbackReason());

        // Verify citation metadata is preserved
        assertEquals(1, response.getSources().size());
        CitationSource source = response.getSources().get(0);
        assertEquals(10L, source.getEventId());
        assertEquals("Tech Summit 2026", source.getEventTitle());
        assertEquals(1L, source.getDocumentId());
        assertEquals("Summit Rules", source.getDocumentTitle());
        assertEquals(DocumentType.RULES, source.getDocumentType());
        assertEquals("event-10-doc-1-chunk-0", source.getChunkId());
        assertEquals(0.88, source.getSimilarityScore(), 0.0001);
    }

    @Test
    @DisplayName("Should return calibrated fallback without calling AI model when evidence is insufficient")
    void shouldReturnFallbackWhenEvidenceInsufficient() {
        SemanticRetrievalResult emptyRetrieval = new SemanticRetrievalResult("what is the prize pool?", List.of(), 5, 0.15);

        when(semanticRetriever.retrievePublicEvidence("what is the prize pool?", null))
                .thenReturn(emptyRetrieval);

        GroundedAnswerResponse response = assistantService.answerQuestion("what is the prize pool?");

        assertFalse(response.isGrounded());
        assertFalse(response.isEvidenceFound());
        assertTrue(response.getAnswer().contains("do not have sufficient information"));
        assertEquals("INSUFFICIENT_EVIDENCE", response.getFallbackReason());
        assertTrue(response.getSources().isEmpty());

        // Crucial: verify model was NEVER invoked
        verifyNoInteractions(aiModelClient);
    }

    @Test
    @DisplayName("Should return citation summary fallback without error when AI model client is unconfigured")
    void shouldReturnFallbackWhenModelUnconfigured() {
        ScoredDocumentChunk scored = new ScoredDocumentChunk(sampleChunk, 0.75);
        SemanticRetrievalResult retrievalResult = new SemanticRetrievalResult("what are the rules?", List.of(scored), 5, 0.75);

        when(semanticRetriever.retrievePublicEvidence("what are the rules?", null))
                .thenReturn(retrievalResult);
        when(aiModelClient.isConfigured()).thenReturn(false);

        GroundedAnswerResponse response = assistantService.answerQuestion("what are the rules?");

        assertFalse(response.isGrounded());
        assertTrue(response.isEvidenceFound());
        assertTrue(response.getAnswer().contains("AI answer generation is currently unavailable"));
        assertTrue(response.getAnswer().contains("Summit Rules"));
        assertEquals("AI_CLIENT_UNCONFIGURED", response.getFallbackReason());
        assertEquals(1, response.getSources().size());

        // Crucial: verify model generation was NOT called
        verify(aiModelClient, never()).generateAnswer(anyString(), anyString());
    }

    @Test
    @DisplayName("Should return citation summary fallback when AI model call fails")
    void shouldReturnFallbackWhenModelCallFails() {
        ScoredDocumentChunk scored = new ScoredDocumentChunk(sampleChunk, 0.82);
        SemanticRetrievalResult retrievalResult = new SemanticRetrievalResult("doors open?", List.of(scored), 5, 0.82);

        when(semanticRetriever.retrievePublicEvidence("doors open?", null))
                .thenReturn(retrievalResult);
        when(aiModelClient.isConfigured()).thenReturn(true);
        when(aiModelClient.generateAnswer(anyString(), anyString()))
                .thenReturn(AiModelResponse.failure("Quota exceeded", "gemini-3.8-flash"));

        GroundedAnswerResponse response = assistantService.answerQuestion("doors open?");

        assertFalse(response.isGrounded());
        assertTrue(response.isEvidenceFound());
        assertTrue(response.getAnswer().contains("AI assistant is temporarily unavailable"));
        assertTrue(response.getAnswer().contains("Summit Rules"));
        assertEquals("AI_CLIENT_ERROR", response.getFallbackReason());
        assertEquals(1, response.getSources().size());
    }

    @Test
    @DisplayName("Should restrict semantic retrieval to specific event when eventId is provided")
    void shouldFilterByEventId() {
        ScoredDocumentChunk scored = new ScoredDocumentChunk(sampleChunk, 0.90);
        SemanticRetrievalResult retrievalResult = new SemanticRetrievalResult("question", List.of(scored), 1, 0.90);

        when(semanticRetriever.retrievePublicEvidence("question", 10L))
                .thenReturn(retrievalResult);
        when(aiModelClient.isConfigured()).thenReturn(true);
        when(aiModelClient.generateAnswer(anyString(), anyString()))
                .thenReturn(AiModelResponse.success("Answer for event 10.", "gemini-3.8-flash"));

        GroundedAnswerResponse response = assistantService.answerQuestion("question", 10L);

        assertTrue(response.isGrounded());
        verify(semanticRetriever).retrievePublicEvidence("question", 10L);
    }

    @Test
    @DisplayName("Should restrict semantic retrieval to specific event and document type when provided")
    void shouldFilterByEventIdAndDocumentType() {
        ScoredDocumentChunk scored = new ScoredDocumentChunk(sampleChunk, 0.93);
        SemanticRetrievalResult retrievalResult = new SemanticRetrievalResult("rules query", List.of(scored), 1, 0.93);

        when(semanticRetriever.retrievePublicEvidence(eq("rules query"), eq(10L), eq(DocumentType.RULES), anyInt(), anyDouble()))
                .thenReturn(retrievalResult);
        when(aiModelClient.isConfigured()).thenReturn(true);
        when(aiModelClient.generateAnswer(anyString(), anyString()))
                .thenReturn(AiModelResponse.success("Answer for event 10 rules.", "gemini-3.8-flash"));

        GroundedAnswerResponse response = assistantService.answerQuestion("rules query", 10L, DocumentType.RULES);

        assertTrue(response.isGrounded());
        verify(semanticRetriever).retrievePublicEvidence(eq("rules query"), eq(10L), eq(DocumentType.RULES), anyInt(), anyDouble());
    }

    @Test
    @DisplayName("Should return invalid request response when question is null or blank")
    void shouldHandleBlankQuestion() {
        GroundedAnswerResponse response = assistantService.answerQuestion("   ");

        assertFalse(response.isGrounded());
        assertFalse(response.isEvidenceFound());
        assertEquals("INVALID_REQUEST", response.getFallbackReason());
        verifyNoInteractions(semanticRetriever);
        verifyNoInteractions(aiModelClient);
    }

    @Test
    @DisplayName("Should answer question for event with no uploaded documents using database fields")
    void shouldAnswerQuestionForEventWithNoDocuments() {
        Event event = new Event();
        event.setId(15L);
        event.setTitle("Global AI Hackathon 2026");
        event.setStatus(EventStatus.PUBLISHED);
        event.setVenue("Tech Convention Center, Hall B");
        event.setCapacity(150);
        event.setDate(LocalDate.of(2026, 11, 20));

        when(eventRepository.findByIdWithDetails(15L)).thenReturn(Optional.of(event));
        SemanticRetrievalResult emptyRetrieval = new SemanticRetrievalResult("what is the venue?", List.of(), 5, 0.0);
        when(semanticRetriever.retrievePublicEvidence("what is the venue?", 15L)).thenReturn(emptyRetrieval);
        when(aiModelClient.isConfigured()).thenReturn(true);
        when(aiModelClient.generateAnswer(anyString(), argThat(prompt -> prompt.contains("Tech Convention Center, Hall B"))))
                .thenReturn(AiModelResponse.success("The event venue is Tech Convention Center, Hall B.", "gemini-3.8-flash"));

        GroundedAnswerResponse response = assistantService.answerQuestion("what is the venue?", 15L);

        assertTrue(response.isGrounded());
        assertTrue(response.isEvidenceFound());
        assertEquals("The event venue is Tech Convention Center, Hall B.", response.getAnswer());
        assertEquals(1, response.getSources().size());
        assertEquals("Event Overview", response.getSources().get(0).getDocumentTitle());
        assertEquals(15L, response.getSources().get(0).getEventId());
        assertEquals("Global AI Hackathon 2026", response.getSources().get(0).getEventTitle());
        verify(aiModelClient).generateAnswer(anyString(), anyString());
    }

    @Test
    @DisplayName("Should answer question directly from event description when no documents exist")
    void shouldAnswerQuestionFromEventDescription() {
        Event event = new Event();
        event.setId(15L);
        event.setTitle("Global AI Hackathon 2026");
        event.setStatus(EventStatus.PUBLISHED);
        event.setDescription("Participants will develop cutting-edge generative AI apps using Python, PyTorch, and Gemini SDK.");

        when(eventRepository.findByIdWithDetails(15L)).thenReturn(Optional.of(event));
        when(semanticRetriever.retrievePublicEvidence("what tools can we use?", 15L))
                .thenReturn(new SemanticRetrievalResult("what tools can we use?", List.of(), 5, 0.0));
        when(aiModelClient.isConfigured()).thenReturn(true);
        when(aiModelClient.generateAnswer(anyString(), argThat(prompt -> prompt.contains("Python, PyTorch, and Gemini SDK"))))
                .thenReturn(AiModelResponse.success("According to the event description, you can use Python, PyTorch, and the Gemini SDK.", "gemini-3.8-flash"));

        GroundedAnswerResponse response = assistantService.answerQuestion("what tools can we use?", 15L);

        assertTrue(response.isGrounded());
        assertTrue(response.isEvidenceFound());
        assertTrue(response.getAnswer().contains("Python, PyTorch"));
        assertEquals("Event Overview", response.getSources().get(0).getDocumentTitle());
    }

    @Test
    @DisplayName("Should accurately report that eligibility criteria are not specified when field is null")
    void shouldReportMissingEligibilityCriteriaAccurately() {
        Event event = new Event();
        event.setId(15L);
        event.setTitle("Global AI Hackathon 2026");
        event.setStatus(EventStatus.PUBLISHED);
        event.setEligibility(null);
        event.setDescription("General hackathon for developers.");

        when(eventRepository.findByIdWithDetails(15L)).thenReturn(Optional.of(event));
        when(semanticRetriever.retrievePublicEvidence("what is the eligibility?", 15L))
                .thenReturn(new SemanticRetrievalResult("what is the eligibility?", List.of(), 5, 0.0));
        when(aiModelClient.isConfigured()).thenReturn(true);
        when(aiModelClient.generateAnswer(anyString(), argThat(prompt -> prompt.contains("Eligibility Criteria: Not explicitly specified in event fields"))))
                .thenReturn(AiModelResponse.success("Eligibility criteria are not specified in the available event details.", "gemini-3.8-flash"));

        GroundedAnswerResponse response = assistantService.answerQuestion("what is the eligibility?", 15L);

        assertTrue(response.isGrounded());
        assertTrue(response.isEvidenceFound());
        assertEquals("Eligibility criteria are not specified in the available event details.", response.getAnswer());
        assertEquals("Event Overview", response.getSources().get(0).getDocumentTitle());
    }

    @Test
    @DisplayName("Should combine event database fields and supplementary document chunks when documents exist")
    void shouldCombineEventDetailsWithSupplementaryDocuments() {
        Event event = new Event();
        event.setId(10L);
        event.setTitle("Tech Summit 2026");
        event.setStatus(EventStatus.PUBLISHED);
        event.setVenue("Auditorium 1");

        ScoredDocumentChunk scored = new ScoredDocumentChunk(sampleChunk, 0.91);
        SemanticRetrievalResult retrievalResult = new SemanticRetrievalResult("summit details", List.of(scored), 1, 0.91);

        when(eventRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(event));
        when(semanticRetriever.retrievePublicEvidence("summit details", 10L)).thenReturn(retrievalResult);
        when(aiModelClient.isConfigured()).thenReturn(true);
        when(aiModelClient.generateAnswer(anyString(), argThat(prompt ->
                prompt.contains("Auditorium 1") && prompt.contains("Summit Rules") && prompt.contains("Badges are mandatory"))))
                .thenReturn(AiModelResponse.success("The summit is held at Auditorium 1. Badges are mandatory per summit rules.", "gemini-3.8-flash"));

        GroundedAnswerResponse response = assistantService.answerQuestion("summit details", 10L);

        assertTrue(response.isGrounded());
        assertTrue(response.isEvidenceFound());
        assertEquals(2, response.getSources().size());
        assertEquals("Event Overview", response.getSources().get(0).getDocumentTitle());
        assertEquals("Summit Rules", response.getSources().get(1).getDocumentTitle());
        assertEquals(0.91, response.getTopSimilarityScore(), 0.0001);
    }
}
