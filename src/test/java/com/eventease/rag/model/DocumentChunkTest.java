package com.eventease.rag.model;

import com.eventease.entity.DocumentType;
import com.eventease.entity.EventStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DocumentChunkTest {

    @Test
    @DisplayName("Should generate stable, deterministic chunk identifier")
    void shouldGenerateStableId() {
        String id1 = DocumentChunk.buildStableId(10L, 5L, 0);
        String id2 = DocumentChunk.buildStableId(10L, 5L, 1);
        String idNull = DocumentChunk.buildStableId(null, null, 0);

        assertEquals("event-10-doc-5-chunk-0", id1);
        assertEquals("event-10-doc-5-chunk-1", id2);
        assertEquals("event-0-doc-0-chunk-0", idNull);
    }

    @Test
    @DisplayName("Should preserve all metadata and correctly report public visibility")
    void shouldPreserveMetadataAndVisibility() {
        LocalDateTime now = LocalDateTime.now();
        DocumentChunk chunkPublished = new DocumentChunk(
                "event-1-doc-2-chunk-0",
                1L,
                "Hackathon 2026",
                EventStatus.PUBLISHED,
                2L,
                "Rules & Regulations",
                DocumentType.RULES,
                0,
                "All submissions must be open source.",
                now
        );

        assertEquals("event-1-doc-2-chunk-0", chunkPublished.getId());
        assertEquals(1L, chunkPublished.getEventId());
        assertEquals("Hackathon 2026", chunkPublished.getEventTitle());
        assertEquals(EventStatus.PUBLISHED, chunkPublished.getEventStatus());
        assertEquals(2L, chunkPublished.getDocumentId());
        assertEquals("Rules & Regulations", chunkPublished.getDocumentTitle());
        assertEquals(DocumentType.RULES, chunkPublished.getDocumentType());
        assertEquals(0, chunkPublished.getChunkIndex());
        assertEquals("All submissions must be open source.", chunkPublished.getText());
        assertEquals(now, chunkPublished.getIndexedAt());
        assertTrue(chunkPublished.isPubliclyVisible());

        DocumentChunk chunkPending = new DocumentChunk(
                "event-2-doc-3-chunk-0",
                2L,
                "Draft Workshop",
                EventStatus.PENDING,
                3L,
                "Draft Notes",
                DocumentType.GUIDELINES,
                0,
                "Internal notes.",
                now
        );
        assertFalse(chunkPending.isPubliclyVisible());

        DocumentChunk chunkCancelled = new DocumentChunk(
                "event-3-doc-4-chunk-0",
                3L,
                "Cancelled Summit",
                EventStatus.CANCELLED,
                4L,
                "Refund FAQ",
                DocumentType.FAQ,
                0,
                "Refund info.",
                now
        );
        assertFalse(chunkCancelled.isPubliclyVisible());
    }

    @Test
    @DisplayName("Should support equality and hash code based on stable ID")
    void shouldSupportEqualityBasedOnId() {
        DocumentChunk chunkA = new DocumentChunk("id-1", 1L, "Event", EventStatus.PUBLISHED, 1L, "Doc", DocumentType.FAQ, 0, "Text", null);
        DocumentChunk chunkB = new DocumentChunk("id-1", 2L, "Other", EventStatus.PENDING, 2L, "OtherDoc", DocumentType.RULES, 1, "Different", null);
        DocumentChunk chunkC = new DocumentChunk("id-2", 1L, "Event", EventStatus.PUBLISHED, 1L, "Doc", DocumentType.FAQ, 0, "Text", null);

        assertEquals(chunkA, chunkB);
        assertEquals(chunkA.hashCode(), chunkB.hashCode());
        assertNotEquals(chunkA, chunkC);
    }

    @Test
    @DisplayName("Should manage embeddings and preserve them during metadata updates")
    void shouldManageEmbeddingsAndMetadataUpdates() {
        float[] vector = new float[]{0.1f, 0.2f, 0.3f};
        DocumentChunk chunk = new DocumentChunk(
                "chunk-1", 1L, "Initial Title", EventStatus.APPROVED, 10L, "Doc", DocumentType.FAQ, 0, "Text", null, vector
        );

        assertTrue(chunk.hasEmbedding());
        assertArrayEquals(vector, chunk.getEmbedding());
        assertFalse(chunk.isPubliclyVisible());

        // Update event metadata
        DocumentChunk updated = chunk.withEventMetadata("Published Title", EventStatus.PUBLISHED);
        assertEquals("Published Title", updated.getEventTitle());
        assertEquals(EventStatus.PUBLISHED, updated.getEventStatus());
        assertTrue(updated.isPubliclyVisible());
        assertTrue(updated.hasEmbedding());
        assertArrayEquals(vector, updated.getEmbedding());

        // withEmbedding
        float[] newVec = new float[]{0.9f, 0.8f};
        DocumentChunk reEmbedded = chunk.withEmbedding(newVec);
        assertArrayEquals(newVec, reEmbedded.getEmbedding());
    }
}
