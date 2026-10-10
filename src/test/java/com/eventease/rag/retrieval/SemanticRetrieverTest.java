package com.eventease.rag.retrieval;

import com.eventease.entity.DocumentType;
import com.eventease.entity.EventStatus;
import com.eventease.rag.config.RagProperties;
import com.eventease.rag.embedding.EmbeddingService;
import com.eventease.rag.index.DocumentIndexService;
import com.eventease.rag.model.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SemanticRetrieverTest {

    @Mock
    private DocumentIndexService indexService;

    @Mock
    private EmbeddingService embeddingService;

    private RagProperties ragProperties;
    private SemanticRetriever retriever;

    private DocumentChunk chunkPublished1;
    private DocumentChunk chunkPublished2;
    private DocumentChunk chunkCompleted;

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties();
        ragProperties.setSimilarityThreshold(0.40);
        ragProperties.setMaxResults(5);

        retriever = new SemanticRetriever(indexService, embeddingService, ragProperties);

        LocalDateTime now = LocalDateTime.now();
        float[] vector1 = new float[]{1.0f, 0.0f, 0.0f};
        float[] vector2 = new float[]{0.0f, 1.0f, 0.0f};

        chunkPublished1 = new DocumentChunk(
                "event-10-doc-1-chunk-0",
                10L,
                "Spring Tech Summit",
                EventStatus.PUBLISHED,
                1L,
                "FAQ",
                DocumentType.FAQ,
                0,
                "Registration is free and open to students.",
                now,
                vector1
        );

        chunkPublished2 = new DocumentChunk(
                "event-10-doc-2-chunk-0",
                10L,
                "Spring Tech Summit",
                EventStatus.PUBLISHED,
                2L,
                "Venue Guidelines",
                DocumentType.GUIDELINES,
                0,
                "The summit will take place in Hall B.",
                now,
                vector2
        );

        chunkCompleted = new DocumentChunk(
                "event-20-doc-3-chunk-0",
                20L,
                "Past Hackathon",
                EventStatus.COMPLETED,
                3L,
                "Archive Rules",
                DocumentType.RULES,
                0,
                "Past hackathon archive rules.",
                now,
                vector1
        );
    }

    @Test
    @DisplayName("Should retrieve only public chunks and rank by cosine similarity")
    void shouldRetrieveAndRankPublicChunks() {
        // Mock public chunks returning only published items
        when(indexService.getPublicChunks()).thenReturn(List.of(chunkPublished1, chunkPublished2));

        float[] queryVector = new float[]{0.9f, 0.1f, 0.0f};
        when(embeddingService.embedText("free registration")).thenReturn(queryVector);

        // chunk1 has higher similarity than chunk2
        when(embeddingService.cosineSimilarity(queryVector, chunkPublished1.getEmbedding())).thenReturn(0.95);
        when(embeddingService.cosineSimilarity(queryVector, chunkPublished2.getEmbedding())).thenReturn(0.10);

        SemanticRetrievalResult result = retriever.retrievePublicEvidence("free registration", null);

        assertTrue(result.hasEvidence());
        // Only chunkPublished1 exceeds 0.40 threshold
        assertEquals(1, result.getChunks().size());
        assertEquals("event-10-doc-1-chunk-0", result.getChunks().get(0).getChunk().getId());
        assertEquals(0.95, result.getChunks().get(0).getSimilarityScore(), 0.0001);
        assertEquals(0.95, result.getTopScore(), 0.0001);
    }

    @Test
    @DisplayName("Should strictly exclude completed or non-published event chunks from public retrieval")
    void shouldExcludeNonPublishedChunks() {
        // getPublicChunks() from DocumentIndexService only returns PUBLISHED events
        when(indexService.getPublicChunks()).thenReturn(List.of(chunkPublished1));

        float[] queryVector = new float[]{1.0f, 0.0f, 0.0f};
        when(embeddingService.embedText("rules")).thenReturn(queryVector);
        when(embeddingService.cosineSimilarity(queryVector, chunkPublished1.getEmbedding())).thenReturn(0.85);

        SemanticRetrievalResult result = retriever.retrievePublicEvidence("rules", null);

        assertEquals(1, result.getChunks().size());
        assertEquals(EventStatus.PUBLISHED, result.getChunks().get(0).getChunk().getEventStatus());
        verify(indexService).getPublicChunks();
        // Completed chunk was never returned by getPublicChunks()
        assertFalse(result.getChunks().stream().anyMatch(c -> c.getChunk().getEventStatus() == EventStatus.COMPLETED));
    }

    @Test
    @DisplayName("Should filter by event ID when specified")
    void shouldFilterByEventId() {
        DocumentChunk chunkOtherEvent = new DocumentChunk(
                "event-30-doc-4-chunk-0",
                30L,
                "Other Event",
                EventStatus.PUBLISHED,
                4L,
                "Guidelines",
                DocumentType.GUIDELINES,
                0,
                "Other event guidelines.",
                LocalDateTime.now(),
                new float[]{1.0f, 0.0f, 0.0f}
        );

        when(indexService.getPublicChunks()).thenReturn(List.of(chunkPublished1, chunkOtherEvent));

        float[] queryVector = new float[]{1.0f, 0.0f, 0.0f};
        when(embeddingService.embedText("guidelines")).thenReturn(queryVector);
        when(embeddingService.cosineSimilarity(queryVector, chunkPublished1.getEmbedding())).thenReturn(0.80);

        // Filter for event 10 only
        SemanticRetrievalResult result = retriever.retrievePublicEvidence("guidelines", 10L);

        assertEquals(1, result.getChunks().size());
        assertEquals(10L, result.getChunks().get(0).getChunk().getEventId());
    }

    @Test
    @DisplayName("Should filter by DocumentType when specified while enforcing public visibility")
    void shouldFilterByDocumentType() {
        when(indexService.getPublicChunks()).thenReturn(List.of(chunkPublished1, chunkPublished2));

        float[] queryVector = new float[]{0.0f, 1.0f, 0.0f};
        when(embeddingService.embedText("venue")).thenReturn(queryVector);
        when(embeddingService.cosineSimilarity(queryVector, chunkPublished2.getEmbedding())).thenReturn(0.92);

        // Retrieve specifically with DocumentType.GUIDELINES
        SemanticRetrievalResult result = retriever.retrievePublicEvidence("venue", null, DocumentType.GUIDELINES, 5, 0.40);

        assertEquals(1, result.getChunks().size());
        assertEquals(DocumentType.GUIDELINES, result.getChunks().get(0).getChunk().getDocumentType());
        assertEquals("event-10-doc-2-chunk-0", result.getChunks().get(0).getChunk().getId());
        assertEquals(0.92, result.getChunks().get(0).getSimilarityScore(), 0.0001);
    }

    @Test
    @DisplayName("Should return empty result when query is null or blank")
    void shouldHandleBlankQuery() {
        SemanticRetrievalResult resultNull = retriever.retrievePublicEvidence(null, null);
        assertFalse(resultNull.hasEvidence());
        assertEquals(0, resultNull.getChunks().size());

        SemanticRetrievalResult resultBlank = retriever.retrievePublicEvidence("   ", null);
        assertFalse(resultBlank.hasEvidence());
        assertEquals(0, resultBlank.getChunks().size());

        verifyNoInteractions(indexService);
    }

    @Test
    @DisplayName("Should return empty result when no chunks meet the similarity threshold")
    void shouldReturnEmptyWhenScoresBelowThreshold() {
        when(indexService.getPublicChunks()).thenReturn(List.of(chunkPublished1));

        float[] queryVector = new float[]{0.0f, 0.0f, 1.0f};
        when(embeddingService.embedText("astronomy telescope")).thenReturn(queryVector);
        // Orthogonal vector yields similarity 0.0, below 0.40 threshold
        when(embeddingService.cosineSimilarity(queryVector, chunkPublished1.getEmbedding())).thenReturn(0.05);

        SemanticRetrievalResult result = retriever.retrievePublicEvidence("astronomy telescope", null);

        assertFalse(result.hasEvidence());
        assertEquals(0, result.getChunks().size());
        assertEquals(0.05, result.getTopScore(), 0.0001);
    }
}
