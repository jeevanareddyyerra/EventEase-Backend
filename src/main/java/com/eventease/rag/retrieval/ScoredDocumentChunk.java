package com.eventease.rag.retrieval;

import com.eventease.rag.model.DocumentChunk;

/**
 * A document chunk paired with its calculated cosine similarity score relative to a search query.
 */
public class ScoredDocumentChunk {

    private final DocumentChunk chunk;
    private final double similarityScore;

    public ScoredDocumentChunk(DocumentChunk chunk, double similarityScore) {
        this.chunk = chunk;
        this.similarityScore = similarityScore;
    }

    public DocumentChunk getChunk() {
        return chunk;
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    @Override
    public String toString() {
        return "ScoredDocumentChunk{" +
                "chunkId='" + (chunk != null ? chunk.getId() : null) + '\'' +
                ", similarityScore=" + String.format("%.4f", similarityScore) +
                '}';
    }
}
