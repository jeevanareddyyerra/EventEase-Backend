package com.eventease.rag.retrieval;

import com.eventease.entity.DocumentType;
import com.eventease.rag.config.RagProperties;
import com.eventease.rag.embedding.EmbeddingService;
import com.eventease.rag.index.DocumentIndexService;
import com.eventease.rag.model.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Performs semantic vector retrieval across indexed event document chunks.
 * Enforces strict public visibility isolation, minimum similarity threshold, and top-K limits.
 */
@Service
@Lazy
public class SemanticRetriever {

    private static final Logger log = LoggerFactory.getLogger(SemanticRetriever.class);

    private final DocumentIndexService indexService;
    private final EmbeddingService embeddingService;
    private final RagProperties ragProperties;

    public SemanticRetriever(
            @Lazy DocumentIndexService indexService,
            @Lazy EmbeddingService embeddingService,
            RagProperties ragProperties
    ) {
        this.indexService = indexService;
        this.embeddingService = embeddingService;
        this.ragProperties = ragProperties;
    }

    /**
     * Retrieves the top-K relevant chunks for public users across all published events.
     * Enforces public visibility: only chunks with eventStatus == PUBLISHED are retrieved.
     *
     * @param query User's natural language question
     * @param eventId Optional event ID filter (null for all published events)
     * @return SemanticRetrievalResult containing ranked, scored chunks above similarity threshold
     */
    public SemanticRetrievalResult retrievePublicEvidence(String query, Long eventId) {
        int maxResults = (ragProperties != null) ? ragProperties.getMaxResults() : 5;
        double threshold = (ragProperties != null) ? ragProperties.getSimilarityThreshold() : 0.40;
        return retrievePublicEvidence(query, eventId, null, maxResults, threshold);
    }

    /**
     * Parameterized retrieval of public chunks with custom limits and thresholds.
     */
    public SemanticRetrievalResult retrievePublicEvidence(
            String query,
            Long eventId,
            DocumentType documentType,
            int maxResults,
            double similarityThreshold
    ) {
        if (query == null || query.trim().isEmpty()) {
            return new SemanticRetrievalResult(query, Collections.emptyList(), 0, 0.0);
        }

        if (embeddingService == null) {
            log.warn("EmbeddingService is null; semantic retrieval cannot compute vectors");
            return new SemanticRetrievalResult(query, Collections.emptyList(), 0, 0.0);
        }

        float[] queryEmbedding = embeddingService.embedText(query);
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            return new SemanticRetrievalResult(query, Collections.emptyList(), 0, 0.0);
        }

        // Strictly enforce public visibility: start from getPublicChunks()
        List<DocumentChunk> candidates = indexService.getPublicChunks();

        // Optional event filter
        if (eventId != null) {
            candidates = candidates.stream()
                    .filter(c -> eventId.equals(c.getEventId()))
                    .toList();
        }

        // Optional document type filter
        if (documentType != null) {
            candidates = candidates.stream()
                    .filter(c -> documentType == c.getDocumentType())
                    .toList();
        }

        if (candidates.isEmpty()) {
            return new SemanticRetrievalResult(query, Collections.emptyList(), 0, 0.0);
        }

        List<ScoredDocumentChunk> scoredList = new ArrayList<>();
        double highestScore = 0.0;

        for (DocumentChunk candidate : candidates) {
            float[] chunkEmbedding = candidate.getEmbedding();
            // If chunk wasn't embedded on indexing, embed on-the-fly
            if (chunkEmbedding == null || chunkEmbedding.length == 0) {
                chunkEmbedding = embeddingService.embedText(candidate.getText());
            }

            double similarity = embeddingService.cosineSimilarity(queryEmbedding, chunkEmbedding);
            if (similarity > highestScore) {
                highestScore = similarity;
            }

            if (similarity >= similarityThreshold) {
                scoredList.add(new ScoredDocumentChunk(candidate, similarity));
            }
        }

        // Sort descending by similarity score
        scoredList.sort(Comparator.comparingDouble(ScoredDocumentChunk::getSimilarityScore).reversed());

        // Limit to maxResults
        List<ScoredDocumentChunk> topChunks = scoredList.stream()
                .limit(Math.max(1, maxResults))
                .toList();

        log.debug("Semantic retrieval for query: candidates={}, passedThreshold={}, topScore={}",
                candidates.size(), scoredList.size(), highestScore);

        return new SemanticRetrievalResult(query, topChunks, candidates.size(), highestScore);
    }
}
