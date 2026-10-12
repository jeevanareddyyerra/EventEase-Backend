package com.eventease.rag.assistant;

import com.eventease.entity.DocumentType;
import com.eventease.rag.client.AiModelClient;
import com.eventease.rag.client.AiModelResponse;
import com.eventease.rag.config.RagProperties;
import com.eventease.rag.model.DocumentChunk;
import com.eventease.rag.prompt.GroundedPromptBuilder;
import com.eventease.rag.retrieval.ScoredDocumentChunk;
import com.eventease.rag.retrieval.SemanticRetrievalResult;
import com.eventease.rag.retrieval.SemanticRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * High-level orchestration service for the EventEase AI assistant.
 * Handles semantic retrieval, insufficient-evidence evaluation, prompt synthesis,
 * model inference, and grounded response packaging with source citations.
 */
@Service
@Lazy
public class AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);

    private final SemanticRetriever semanticRetriever;
    private final GroundedPromptBuilder promptBuilder;
    private final AiModelClient aiModelClient;
    private final RagProperties ragProperties;

    public AiAssistantService(
            @Lazy SemanticRetriever semanticRetriever,
            GroundedPromptBuilder promptBuilder,
            AiModelClient aiModelClient,
            RagProperties ragProperties
    ) {
        this.semanticRetriever = semanticRetriever;
        this.promptBuilder = promptBuilder;
        this.aiModelClient = aiModelClient;
        this.ragProperties = ragProperties;
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
     *
     * @param question Natural language question from the user
     * @param eventId Optional event ID filter
     * @param documentType Optional document type filter
     * @return GroundedAnswerResponse with answer and citations
     */
    public GroundedAnswerResponse answerQuestion(String question, Long eventId, DocumentType documentType) {
        if (question == null || question.trim().isEmpty()) {
            return GroundedAnswerResponse.invalidRequest("Please provide a question.");
        }

        log.debug("Processing user question: length={}, eventFilter={}, documentTypeFilter={}",
                question.length(), eventId, documentType);

        // 1. Semantic retrieval across public event chunks with optional filters
        SemanticRetrievalResult retrievalResult;
        if (documentType != null) {
            int maxResults = (ragProperties != null) ? ragProperties.getMaxResults() : 5;
            double threshold = (ragProperties != null) ? ragProperties.getSimilarityThreshold() : 0.40;
            retrievalResult = semanticRetriever.retrievePublicEvidence(
                    question.trim(), eventId, documentType, maxResults, threshold);
        } else {
            retrievalResult = semanticRetriever.retrievePublicEvidence(question.trim(), eventId);
        }

        // 2. Insufficient evidence guard: if no chunks met similarity threshold, return calibrated fallback
        if (!retrievalResult.hasEvidence()) {
            log.debug("No evidence met the similarity threshold (topScore={}). Returning insufficient evidence response.",
                    retrievalResult.getTopScore());
            return GroundedAnswerResponse.insufficientEvidence(retrievalResult.getTopScore());
        }

        // 3. Extract citation metadata from retrieved evidence
        List<ScoredDocumentChunk> scoredChunks = retrievalResult.getChunks();
        List<DocumentChunk> evidenceChunks = new ArrayList<>(scoredChunks.size());
        List<CitationSource> sources = new ArrayList<>(scoredChunks.size());

        for (ScoredDocumentChunk scored : scoredChunks) {
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

        // 4. Check if AI Model Client is configured
        if (aiModelClient == null || !aiModelClient.isConfigured()) {
            log.debug("AI model client is unconfigured or disabled. Returning evidence source summary.");
            String sourceSummary = sources.stream()
                    .map(s -> String.format("\"%s\" (%s for %s)", s.getDocumentTitle(), s.getDocumentType(), s.getEventTitle()))
                    .distinct()
                    .collect(Collectors.joining(", "));
            String fallbackAnswer = "AI answer generation is currently unavailable. Relevant documentation was found in: " + sourceSummary + ".";
            return GroundedAnswerResponse.fallback(fallbackAnswer, sources, retrievalResult.getTopScore(), "AI_CLIENT_UNCONFIGURED");
        }

        // 5. Construct grounded prompt
        String systemInstruction = promptBuilder.buildSystemInstruction();
        String userPrompt = promptBuilder.buildUserPrompt(question.trim(), evidenceChunks);

        // 6. Invoke AI model
        AiModelResponse modelResponse = aiModelClient.generateAnswer(systemInstruction, userPrompt);

        // 7. Handle model failure (timeout, network, quota) gracefully
        if (!modelResponse.isSuccessful() || modelResponse.getText() == null || modelResponse.getText().trim().isEmpty()) {
            log.warn("AI model completion failed: {}. Returning evidence citation fallback.", modelResponse.getErrorMessage());
            String sourceSummary = sources.stream()
                    .map(s -> String.format("\"%s\" (%s for %s)", s.getDocumentTitle(), s.getDocumentType(), s.getEventTitle()))
                    .distinct()
                    .collect(Collectors.joining(", "));
            String fallbackAnswer = "The AI assistant is temporarily unavailable. Relevant documentation was found in: " + sourceSummary + ".";
            return GroundedAnswerResponse.fallback(fallbackAnswer, sources, retrievalResult.getTopScore(), "AI_CLIENT_ERROR");
        }

        // 8. Return successfully grounded answer with sources
        return GroundedAnswerResponse.grounded(
                modelResponse.getText().trim(),
                sources,
                retrievalResult.getTopScore(),
                modelResponse.getModelName()
        );
    }
}
