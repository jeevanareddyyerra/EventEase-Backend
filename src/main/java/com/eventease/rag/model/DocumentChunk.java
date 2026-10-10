package com.eventease.rag.model;

import com.eventease.entity.DocumentType;
import com.eventease.entity.EventStatus;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents an indexed, searchable text chunk derived from an EventDocument,
 * along with its optional semantic embedding vector.
 */
public class DocumentChunk {

    private final String id;
    private final Long eventId;
    private final String eventTitle;
    private final EventStatus eventStatus;
    private final Long documentId;
    private final String documentTitle;
    private final DocumentType documentType;
    private final int chunkIndex;
    private final String text;
    private final LocalDateTime indexedAt;
    private final float[] embedding;

    public DocumentChunk(
            String id,
            Long eventId,
            String eventTitle,
            EventStatus eventStatus,
            Long documentId,
            String documentTitle,
            DocumentType documentType,
            int chunkIndex,
            String text,
            LocalDateTime indexedAt
    ) {
        this(id, eventId, eventTitle, eventStatus, documentId, documentTitle, documentType, chunkIndex, text, indexedAt, null);
    }

    public DocumentChunk(
            String id,
            Long eventId,
            String eventTitle,
            EventStatus eventStatus,
            Long documentId,
            String documentTitle,
            DocumentType documentType,
            int chunkIndex,
            String text,
            LocalDateTime indexedAt,
            float[] embedding
    ) {
        this.id = id;
        this.eventId = eventId;
        this.eventTitle = eventTitle;
        this.eventStatus = eventStatus;
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.documentType = documentType;
        this.chunkIndex = chunkIndex;
        this.text = text;
        this.indexedAt = indexedAt != null ? indexedAt : LocalDateTime.now();
        this.embedding = embedding;
    }

    public static String buildStableId(Long eventId, Long documentId, int chunkIndex) {
        return String.format("event-%d-doc-%d-chunk-%d", eventId != null ? eventId : 0L, documentId != null ? documentId : 0L, chunkIndex);
    }

    public String getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public EventStatus getEventStatus() {
        return eventStatus;
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

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getText() {
        return text;
    }

    public LocalDateTime getIndexedAt() {
        return indexedAt;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public boolean hasEmbedding() {
        return embedding != null && embedding.length > 0;
    }

    public boolean isPubliclyVisible() {
        return eventStatus == EventStatus.PUBLISHED;
    }

    /**
     * Creates a copy of this chunk with the specified embedding vector.
     */
    public DocumentChunk withEmbedding(float[] newEmbedding) {
        return new DocumentChunk(
                id,
                eventId,
                eventTitle,
                eventStatus,
                documentId,
                documentTitle,
                documentType,
                chunkIndex,
                text,
                indexedAt,
                newEmbedding
        );
    }

    /**
     * Creates a copy of this chunk with updated event metadata, preserving the embedding.
     */
    public DocumentChunk withEventMetadata(String newTitle, EventStatus newStatus) {
        return new DocumentChunk(
                id,
                eventId,
                newTitle != null ? newTitle : eventTitle,
                newStatus != null ? newStatus : eventStatus,
                documentId,
                documentTitle,
                documentType,
                chunkIndex,
                text,
                indexedAt,
                embedding
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentChunk that = (DocumentChunk) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DocumentChunk{" +
                "id='" + id + '\'' +
                ", eventId=" + eventId +
                ", eventTitle='" + eventTitle + '\'' +
                ", eventStatus=" + eventStatus +
                ", documentId=" + documentId +
                ", documentTitle='" + documentTitle + '\'' +
                ", documentType=" + documentType +
                ", chunkIndex=" + chunkIndex +
                ", textLength=" + (text != null ? text.length() : 0) +
                ", hasEmbedding=" + hasEmbedding() +
                '}';
    }
}
