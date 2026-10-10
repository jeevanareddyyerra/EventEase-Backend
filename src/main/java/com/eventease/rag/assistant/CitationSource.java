package com.eventease.rag.assistant;

import com.eventease.entity.DocumentType;

/**
 * Metadata citation describing the specific event document chunk that supported an answer.
 */
public class CitationSource {

    private final Long eventId;
    private final String eventTitle;
    private final Long documentId;
    private final String documentTitle;
    private final DocumentType documentType;
    private final String chunkId;
    private final double similarityScore;

    public CitationSource(
            Long eventId,
            String eventTitle,
            Long documentId,
            String documentTitle,
            DocumentType documentType,
            String chunkId,
            double similarityScore
    ) {
        this.eventId = eventId;
        this.eventTitle = eventTitle;
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.documentType = documentType;
        this.chunkId = chunkId;
        this.similarityScore = similarityScore;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getChunkId() {
        return chunkId;
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    @Override
    public String toString() {
        return "CitationSource{" +
                "eventId=" + eventId +
                ", eventTitle='" + eventTitle + '\'' +
                ", documentId=" + documentId +
                ", documentTitle='" + documentTitle + '\'' +
                ", documentType=" + documentType +
                ", chunkId='" + chunkId + '\'' +
                ", similarityScore=" + String.format("%.4f", similarityScore) +
                '}';
    }
}
