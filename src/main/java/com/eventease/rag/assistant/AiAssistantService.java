package com.eventease.rag.assistant;

import com.eventease.entity.DocumentType;
import com.eventease.entity.Event;
import com.eventease.entity.EventStatus;
import com.eventease.rag.client.AiModelClient;
import com.eventease.rag.client.AiModelResponse;
import com.eventease.rag.config.RagProperties;
import com.eventease.rag.model.DocumentChunk;
import com.eventease.rag.prompt.GroundedPromptBuilder;
import com.eventease.rag.retrieval.ScoredDocumentChunk;
import com.eventease.rag.retrieval.SemanticRetrievalResult;
import com.eventease.rag.retrieval.SemanticRetriever;
import com.eventease.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * High-level orchestration service for the EventEase AI assistant.
 * Handles semantic retrieval, insufficient-evidence evaluation, prompt synthesis,
 * model inference, and grounded response packaging with source citations.
 * Incorporates authoritative event database fields as the primary source,
 * complemented by supplementary document retrieval when available.
 */
@Service
@Lazy
public class AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);

    private final SemanticRetriever semanticRetriever;
    private final GroundedPromptBuilder promptBuilder;
    private final AiModelClient aiModelClient;
    private final RagProperties ragProperties;
    private final EventRepository eventRepository;

    public AiAssistantService(
            @Lazy SemanticRetriever semanticRetriever,
            GroundedPromptBuilder promptBuilder,
            AiModelClient aiModelClient,
            RagProperties ragProperties
    ) {
        this(semanticRetriever, promptBuilder, aiModelClient, ragProperties, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AiAssistantService(
            @Lazy SemanticRetriever semanticRetriever,
            GroundedPromptBuilder promptBuilder,
            AiModelClient aiModelClient,
            RagProperties ragProperties,
            @Lazy EventRepository eventRepository
    ) {
        this.semanticRetriever = semanticRetriever;
        this.promptBuilder = promptBuilder;
        this.aiModelClient = aiModelClient;
        this.ragProperties = ragProperties;
        this.eventRepository = eventRepository;
    }

    /**
     * Answers a user question grounded in all published event documentation.
     *
     * @param question Natural language question from the user
     * @return GroundedAnswerResponse with answer and citations
     */
    public GroundedAnswerResponse answerQuestion(String question) {
        return answerQuestion(question, null, null);
    }

    /**
     * Answers a user question restricted to a specific event's published documentation.
     *
     * @param question Natural language question from the user
     * @param eventId Optional event ID filter
     * @return GroundedAnswerResponse with answer and citations
     */
    public GroundedAnswerResponse answerQuestion(String question, Long eventId) {
        return answerQuestion(question, eventId, null);
    }

    /**
     * Answers a user question restricted to a specific event and document type.
     * Uses event database fields as the primary authoritative source, with supplementary
     * document chunks if available.
     *
     * @param question Natural language question from the user
     * @param eventId Optional event ID filter
     * @param documentType Optional document type filter
     * @return GroundedAnswerResponse with answer and citations
     */
    @Transactional(readOnly = true)
    public GroundedAnswerResponse answerQuestion(String question, Long eventId, DocumentType documentType) {
        if (question == null || question.trim().isEmpty()) {
            return GroundedAnswerResponse.invalidRequest("Please provide a question.");
        }

        log.debug("Processing user question: length={}, eventFilter={}, documentTypeFilter={}",
                question.length(), eventId, documentType);

        // 1. Retrieve authoritative event details from database if eventId is provided
        Event event = null;
        if (eventId != null && eventRepository != null) {
            event = eventRepository.findByIdWithDetails(eventId)
                    .or(() -> eventRepository.findById(eventId))
                    .orElse(null);
            if (event != null && event.getStatus() != EventStatus.PUBLISHED) {
                log.warn("Event #{} has non-published status {}. Skipping event details for public assistant.",
                        eventId, event.getStatus());
                event = null;
            }
        }

        // 2. Supplementary semantic retrieval across public event chunks with optional filters
        SemanticRetrievalResult retrievalResult = null;
        if (semanticRetriever != null) {
            if (documentType != null) {
                int maxResults = (ragProperties != null) ? ragProperties.getMaxResults() : 5;
                double threshold = (ragProperties != null) ? ragProperties.getSimilarityThreshold() : 0.40;
                retrievalResult = semanticRetriever.retrievePublicEvidence(
                        question.trim(), eventId, documentType, maxResults, threshold);
            } else {
                retrievalResult = semanticRetriever.retrievePublicEvidence(question.trim(), eventId);
            }
        }

        boolean hasDocumentEvidence = retrievalResult != null && retrievalResult.hasEvidence();
        boolean hasEventData = (event != null);

        // 3. Insufficient evidence guard: if neither event data nor document evidence is available, return fallback
        if (!hasEventData && !hasDocumentEvidence) {
            double topScore = (retrievalResult != null) ? retrievalResult.getTopScore() : 0.0;
            log.debug("Neither event data nor document evidence met threshold (topScore={}). Returning insufficient evidence response.",
                    topScore);
            return GroundedAnswerResponse.insufficientEvidence(topScore);
        }

        // 4. Assemble citations and evidence chunks
        List<DocumentChunk> evidenceChunks = new ArrayList<>();
        List<CitationSource> sources = new ArrayList<>();

        // If event data is present, cite Event Overview as primary source
        if (hasEventData) {
            sources.add(new CitationSource(
                    event.getId(),
                    event.getTitle(),
                    null,
                    "Event Overview",
                    null,
                    "event-" + event.getId() + "-overview",
                    1.0
            ));
        }

        // If document chunks matched, add them as supplementary citations
        if (hasDocumentEvidence) {
            for (ScoredDocumentChunk scored : retrievalResult.getChunks()) {
                DocumentChunk chunk = scored.getChunk();
                evidenceChunks.add(chunk);
                sources.add(new CitationSource(
                        chunk.getEventId(),
                        chunk.getEventTitle(),
                        chunk.getDocumentId(),
                        chunk.getDocumentTitle(),
                        chunk.getDocumentType(),
                        chunk.getId(),
                        scored.getSimilarityScore()
                ));
            }
        }

        double topScore = hasDocumentEvidence ? retrievalResult.getTopScore() : 1.0;

        // 5. Check if AI Model Client is configured
        if (aiModelClient == null || !aiModelClient.isConfigured()) {
            log.debug("AI model client is unconfigured or disabled. Returning evidence source summary.");
            String fallbackAnswer;
            if (hasEventData && !hasDocumentEvidence) {
                fallbackAnswer = "AI answer generation is currently unavailable. Event details for \"" + event.getTitle() + "\" are available on the event page.";
            } else {
                String sourceSummary = sources.stream()
                        .map(s -> s.getDocumentTitle() != null ? String.format("\"%s\"", s.getDocumentTitle()) : s.getEventTitle())
                        .distinct()
                        .collect(Collectors.joining(", "));
                fallbackAnswer = "AI answer generation is currently unavailable. Relevant documentation was found in: " + sourceSummary + ".";
            }
            return GroundedAnswerResponse.fallback(fallbackAnswer, sources, topScore, "AI_CLIENT_UNCONFIGURED");
        }

        // 6. Construct grounded prompt with event details and supplementary evidence
        String systemInstruction = promptBuilder.buildSystemInstruction();
        String userPrompt = promptBuilder.buildUserPrompt(question.trim(), event, evidenceChunks);

        // 7. Invoke AI model
        AiModelResponse modelResponse = aiModelClient.generateAnswer(systemInstruction, userPrompt);

        // 8. Handle model failure (timeout, network, quota) gracefully
        if (!modelResponse.isSuccessful() || modelResponse.getText() == null || modelResponse.getText().trim().isEmpty()) {
            log.warn("AI model completion failed: {}. Returning evidence citation fallback.", modelResponse.getErrorMessage());
            String fallbackAnswer;
            if (hasEventData && !hasDocumentEvidence) {
                fallbackAnswer = "The AI assistant is temporarily unavailable. Please refer to the event overview for \"" + event.getTitle() + "\".";
            } else {
                String sourceSummary = sources.stream()
                        .map(s -> s.getDocumentTitle() != null ? String.format("\"%s\"", s.getDocumentTitle()) : s.getEventTitle())
                        .distinct()
                        .collect(Collectors.joining(", "));
                fallbackAnswer = "The AI assistant is temporarily unavailable. Relevant documentation was found in: " + sourceSummary + ".";
            }
            return GroundedAnswerResponse.fallback(fallbackAnswer, sources, topScore, "AI_CLIENT_ERROR");
        }

        // 9. Return successfully grounded answer with sources
        return GroundedAnswerResponse.grounded(
                modelResponse.getText().trim(),
                sources,
                topScore,
                modelResponse.getModelName()
        );
    }
}
