package com.eventease.rag.retrieval;

import java.util.Collections;
import java.util.List;

/**
 * Result of a semantic retrieval query containing ranked chunks, statistics, and top score.
 */
public class SemanticRetrievalResult {

    private final String query;
    private final List<ScoredDocumentChunk> chunks;
    private final int totalCandidatesEvaluated;
    private final double topScore;

    public SemanticRetrievalResult(
            String query,
            List<ScoredDocumentChunk> chunks,
            int totalCandidatesEvaluated,
            double topScore
    ) {
        this.query = query;
        this.chunks = chunks != null ? Collections.unmodifiableList(chunks) : Collections.emptyList();
        this.totalCandidatesEvaluated = totalCandidatesEvaluated;
        this.topScore = topScore;
    }

    public String getQuery() {
        return query;
    }

    public List<ScoredDocumentChunk> getChunks() {
        return chunks;
    }

    public int getTotalCandidatesEvaluated() {
        return totalCandidatesEvaluated;
    }

    public double getTopScore() {
        return topScore;
    }

    public boolean hasEvidence() {
        return !chunks.isEmpty();
    }
}
