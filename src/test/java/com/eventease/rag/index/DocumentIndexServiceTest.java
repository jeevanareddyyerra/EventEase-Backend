package com.eventease.rag.index;

import com.eventease.entity.DocumentType;
import com.eventease.entity.Event;
import com.eventease.entity.EventDocument;
import com.eventease.entity.EventStatus;
import com.eventease.rag.chunking.DocumentChunker;
import com.eventease.rag.model.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DocumentIndexServiceTest {

    private DocumentChunker chunker;
    private DocumentIndexService indexService;

    private Event publishedEvent;
    private Event pendingEvent;

    @BeforeEach
    void setUp() {
        chunker = new DocumentChunker(100, 20);
        indexService = new DocumentIndexService(chunker);

        publishedEvent = new Event();
        publishedEvent.setId(10L);
        publishedEvent.setTitle("AI Symposium 2026");
        publishedEvent.setStatus(EventStatus.PUBLISHED);

        pendingEvent = new Event();
        pendingEvent.setId(20L);
        pendingEvent.setTitle("Draft Workshop");
        pendingEvent.setStatus(EventStatus.PENDING);
    }

    @Test
    @DisplayName("Should index published document and return in public chunks")
    void shouldIndexPublishedDocument() {
        EventDocument doc = new EventDocument(
                publishedEvent,
                "Symposium FAQ",
                DocumentType.FAQ,
                "What is the venue? The venue is Central Hall. Is lunch provided? Yes, lunch is provided.",
                null
        );
        doc.setId(100L);

        indexService.indexDocument(doc);

        assertEquals(1, indexService.getDocumentCount());
        assertTrue(indexService.getChunkCount() >= 1);

        List<DocumentChunk> publicChunks = indexService.getPublicChunks();
        assertFalse(publicChunks.isEmpty());
        assertEquals("AI Symposium 2026", publicChunks.get(0).getEventTitle());
        assertEquals(DocumentType.FAQ, publicChunks.get(0).getDocumentType());
        assertEquals(100L, publicChunks.get(0).getDocumentId());
    }

    @Test
    @DisplayName("Should exclude pending or draft event documents from public chunks")
    void shouldExcludePendingEventDocumentsFromPublicChunks() {
        EventDocument doc = new EventDocument(
                pendingEvent,
                "Internal Draft Guidelines",
                DocumentType.GUIDELINES,
                "Draft internal instructions for organizers only.",
                null
        );
        doc.setId(200L);

        indexService.indexDocument(doc);

        assertEquals(1, indexService.getDocumentCount());
        assertTrue(indexService.getChunkCount() >= 1);

        // Public retrieval must be empty
        List<DocumentChunk> publicChunks = indexService.getPublicChunks();
        assertTrue(publicChunks.isEmpty(), "Pending event chunks must NOT be publicly visible!");

        // But accessible to internal/event lookup
        List<DocumentChunk> eventChunks = indexService.getChunksByEventId(20L);
        assertFalse(eventChunks.isEmpty());
    }

    @Test
    @DisplayName("Should replace all chunks atomically on update without leaving stale chunks")
    void shouldReplaceChunksOnDocumentUpdate() {
        EventDocument doc = new EventDocument(
                publishedEvent,
                "Rules v1",
                DocumentType.RULES,
                "Rule 1: Always be on time. Rule 2: Bring laptop. Rule 3: Wear badge.",
                null
        );
        doc.setId(300L);
        indexService.indexDocument(doc);

        int initialChunkCount = indexService.getChunkCount();
        assertTrue(initialChunkCount >= 1);

        // Update document with new content
        doc.setTitle("Rules v2");
        doc.setContent("Rule 1: Virtual event only. No physical venue attendance allowed.");
        indexService.indexDocument(doc);

        // Document count remains 1
        assertEquals(1, indexService.getDocumentCount());
        List<DocumentChunk> updatedChunks = indexService.getChunksByDocumentId(300L);

        assertFalse(updatedChunks.isEmpty());
        // Verify no stale v1 text remains
        for (DocumentChunk chunk : updatedChunks) {
            assertEquals("Rules v2", chunk.getDocumentTitle());
            assertFalse(chunk.getText().contains("Wear badge"));
            assertTrue(chunk.getText().contains("Virtual event only"));
        }
    }

    @Test
    @DisplayName("Should remove all document chunks when document is deleted")
    void shouldRemoveDocumentChunks() {
        EventDocument doc = new EventDocument(
                publishedEvent,
                "Schedule",
                DocumentType.GUIDELINES,
                "Morning keynote at 9 AM. Lunch at 1 PM.",
                null
        );
        doc.setId(400L);
        indexService.indexDocument(doc);
        assertEquals(1, indexService.getDocumentCount());

        indexService.removeDocument(400L);

        assertEquals(0, indexService.getDocumentCount());
        assertEquals(0, indexService.getChunkCount());
        assertTrue(indexService.getChunksByDocumentId(400L).isEmpty());
    }

    @Test
    @DisplayName("Should remove all event documents when event is removed")
    void shouldRemoveEventDocuments() {
        EventDocument doc1 = new EventDocument(publishedEvent, "Doc 1", DocumentType.FAQ, "Question 1?", null);
        doc1.setId(501L);
        EventDocument doc2 = new EventDocument(publishedEvent, "Doc 2", DocumentType.RULES, "Rule 1.", null);
        doc2.setId(502L);

        indexService.indexDocument(doc1);
        indexService.indexDocument(doc2);
        assertEquals(2, indexService.getDocumentCount());

        indexService.removeEventDocuments(publishedEvent.getId());

        assertEquals(0, indexService.getDocumentCount());
        assertEquals(0, indexService.getChunkCount());
    }

    @Test
    @DisplayName("Should safely handle null or blank content by removing previous chunks")
    void shouldHandleNullOrBlankContent() {
        EventDocument doc = new EventDocument(publishedEvent, "Doc", DocumentType.FAQ, "Initial content", null);
        doc.setId(600L);
        indexService.indexDocument(doc);
        assertEquals(1, indexService.getDocumentCount());

        // Update with blank content
        doc.setContent("   ");
        indexService.indexDocument(doc);

        assertEquals(0, indexService.getDocumentCount());
        assertEquals(0, indexService.getChunkCount());
    }

    @Test
    @DisplayName("Should rebuild index safely and isolate individual corrupted documents")
    void shouldRebuildIndexSafelyWithIsolatedFailures() {
        EventDocument validDoc1 = new EventDocument(publishedEvent, "Valid 1", DocumentType.FAQ, "Valid FAQ content", null);
        validDoc1.setId(701L);

        EventDocument corruptedDoc = new EventDocument(publishedEvent, "Corrupt", DocumentType.RULES, null, null);
        corruptedDoc.setId(702L);

        EventDocument validDoc2 = new EventDocument(publishedEvent, "Valid 2", DocumentType.RULES, "Valid Rules content", null);
        validDoc2.setId(703L);

        indexService.rebuildIndex(Arrays.asList(validDoc1, corruptedDoc, validDoc2, null));

        assertEquals(2, indexService.getDocumentCount());
        assertTrue(indexService.getChunkCount() >= 2);
        assertFalse(indexService.getChunksByDocumentId(701L).isEmpty());
        assertFalse(indexService.getChunksByDocumentId(703L).isEmpty());
        assertTrue(indexService.getChunksByDocumentId(702L).isEmpty());
    }

    @Test
    @DisplayName("Should handle concurrent indexing and removals thread-safely")
    void shouldHandleConcurrentOperationsThreadSafely() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final long docIdOffset = t * 1000L;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        EventDocument doc = new EventDocument(
                                publishedEvent,
                                "Doc " + i,
                                DocumentType.FAQ,
                                "Content for document " + i + " with some detailed words.",
                                null
                        );
                        doc.setId(docIdOffset + i);
                        indexService.indexDocument(doc);

                        if (i % 5 == 0) {
                            indexService.removeDocument(docIdOffset + i);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        // Index should be in a consistent non-corrupted state
        assertTrue(indexService.getChunkCount() > 0);
        assertTrue(indexService.getDocumentCount() > 0);
    }

    @Test
    @DisplayName("Should immediately exclude chunks from public retrieval when event status transitions to COMPLETED")
    void shouldExcludeChunksFromPublicWhenEventCompleted() {
        EventDocument doc = new EventDocument(
                publishedEvent,
                "Symposium Guide",
                DocumentType.GUIDELINES,
                "Detailed conference guide with important information for attendees.",
                null
        );
        doc.setId(800L);
        indexService.indexDocument(doc);

        assertFalse(indexService.getPublicChunks().isEmpty(), "Initially published chunks must be public");

        // Transition event to COMPLETED
        indexService.updateEventStatus(publishedEvent.getId(), EventStatus.COMPLETED);

        // Chunks must immediately be excluded from public queries
        assertTrue(indexService.getPublicChunks().isEmpty(), "COMPLETED event chunks must NOT be publicly visible!");

        // Internal lookup by event ID still shows the chunk with updated status
        List<DocumentChunk> eventChunks = indexService.getChunksByEventId(publishedEvent.getId());
        assertFalse(eventChunks.isEmpty());
        assertEquals(EventStatus.COMPLETED, eventChunks.get(0).getEventStatus());
        assertFalse(eventChunks.get(0).isPubliclyVisible());
    }

    @Test
    @DisplayName("Should make chunks publicly visible when event status transitions to PUBLISHED")
    void shouldMakeChunksPublicWhenEventPublished() {
        Event approvedEvent = new Event();
        approvedEvent.setId(30L);
        approvedEvent.setTitle("Approved Summit");
        approvedEvent.setStatus(EventStatus.APPROVED);

        EventDocument doc = new EventDocument(
                approvedEvent,
                "Summit Schedule",
                DocumentType.GUIDELINES,
                "Summit starts at 10 AM in the Main Auditorium.",
                null
        );
        doc.setId(850L);
        indexService.indexDocument(doc);

        // Initially APPROVED, so not public
        assertTrue(indexService.getPublicChunks().isEmpty());

        // Transition to PUBLISHED
        indexService.updateEventStatus(30L, EventStatus.PUBLISHED);

        // Now must be in public chunks
        List<DocumentChunk> publicChunks = indexService.getPublicChunks();
        assertFalse(publicChunks.isEmpty());
        assertEquals(EventStatus.PUBLISHED, publicChunks.get(0).getEventStatus());
        assertTrue(publicChunks.get(0).isPubliclyVisible());
    }

    @Test
    @DisplayName("Should update event title across all indexed chunks when event metadata is updated")
    void shouldUpdateEventMetadataTitle() {
        EventDocument doc = new EventDocument(
                publishedEvent,
                "Doc 1",
                DocumentType.RULES,
                "Rule details for participants.",
                null
        );
        doc.setId(900L);
        indexService.indexDocument(doc);

        assertEquals("AI Symposium 2026", indexService.getChunksByDocumentId(900L).get(0).getEventTitle());

        // Update event title
        indexService.updateEventMetadata(publishedEvent.getId(), "Global AI Symposium 2026", null);

        assertEquals("Global AI Symposium 2026", indexService.getChunksByDocumentId(900L).get(0).getEventTitle());
        assertEquals(EventStatus.PUBLISHED, indexService.getChunksByDocumentId(900L).get(0).getEventStatus());
    }

    @Test
    @DisplayName("Should evict prior chunks when document indexing fails to prevent stale chunks")
    void shouldEvictPriorChunksWhenIndexingFails() {
        // Create an index service with a mock chunker that fails on specific text
        DocumentChunker mockChunker = org.mockito.Mockito.mock(DocumentChunker.class);
        DocumentIndexService serviceWithMock = new DocumentIndexService(mockChunker);

        org.mockito.Mockito.when(mockChunker.chunkText("Valid content"))
                .thenReturn(List.of("Valid chunk 1", "Valid chunk 2"));
        org.mockito.Mockito.when(mockChunker.chunkText("Exploding content"))
                .thenThrow(new RuntimeException("Simulated parser failure"));

        EventDocument doc = new EventDocument(publishedEvent, "Title", DocumentType.FAQ, "Valid content", null);
        doc.setId(950L);

        // First index succeeds
        serviceWithMock.indexDocument(doc);
        assertEquals(2, serviceWithMock.getChunkCount());
        assertEquals(1, serviceWithMock.getDocumentCount());

        // Update document with bad content causing exception
        doc.setContent("Exploding content");
        assertThrows(RuntimeException.class, () -> serviceWithMock.indexDocument(doc));

        // Prior chunks must be completely evicted, NOT left stale
        assertEquals(0, serviceWithMock.getChunkCount(), "Stale chunks must be evicted upon indexing failure!");
        assertEquals(0, serviceWithMock.getDocumentCount());
        assertTrue(serviceWithMock.getChunksByDocumentId(950L).isEmpty());
    }

    @Test
    @DisplayName("Should never expose an empty index to reader threads during an atomic rebuild")
    void shouldNeverExposeEmptyIndexToReadersDuringRebuild() throws InterruptedException {
        // Pre-populate index with documents
        for (int i = 0; i < 5; i++) {
            EventDocument doc = new EventDocument(
                    publishedEvent,
                    "Pre-doc " + i,
                    DocumentType.FAQ,
                    "Some valid text content for pre-populated document " + i,
                    null
            );
            doc.setId(1000L + i);
            indexService.indexDocument(doc);
        }

        assertTrue(indexService.getPublicChunks().size() >= 5);

        int readerThreads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(readerThreads + 1);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(readerThreads);
        java.util.concurrent.atomic.AtomicBoolean observedEmptyIndex = new java.util.concurrent.atomic.AtomicBoolean(false);

        // Start reader threads continuously reading
        for (int r = 0; r < readerThreads; r++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int iter = 0; iter < 100; iter++) {
                        List<DocumentChunk> chunks = indexService.getPublicChunks();
                        if (chunks.isEmpty()) {
                            observedEmptyIndex.set(true);
                        }
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Prepare rebuild list with same number of documents
        List<EventDocument> rebuildDocs = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            EventDocument doc = new EventDocument(
                    publishedEvent,
                    "Rebuild-doc " + i,
                    DocumentType.FAQ,
                    "Rebuilt content for document " + i,
                    null
            );
            doc.setId(1000L + i);
            rebuildDocs.add(doc);
        }

        startLatch.countDown();
        // Rebuild index concurrently with readers
        indexService.rebuildIndex(rebuildDocs);

        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertFalse(observedEmptyIndex.get(), "Reader threads must never observe an empty index during atomic rebuild swap!");
    }

    @Test
    @DisplayName("Should generate and store embeddings when EmbeddingService is present and preserve them during metadata updates")
    void shouldGenerateAndPreserveEmbeddingsWithEmbeddingService() {
        com.eventease.rag.embedding.EmbeddingService mockEmbedding = org.mockito.Mockito.mock(com.eventease.rag.embedding.EmbeddingService.class);
        float[] sampleVec = new float[]{0.5f, 0.5f, 0.5f};
        org.mockito.Mockito.when(mockEmbedding.embedText(org.mockito.ArgumentMatchers.anyString())).thenReturn(sampleVec);

        DocumentIndexService serviceWithEmbedding = new DocumentIndexService(chunker, mockEmbedding);

        EventDocument doc = new EventDocument(publishedEvent, "Embedded Doc", DocumentType.FAQ, "Sample content for embedding", null);
        doc.setId(1100L);

        serviceWithEmbedding.indexDocument(doc);

        List<DocumentChunk> chunks = serviceWithEmbedding.getChunksByDocumentId(1100L);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(0).hasEmbedding());
        assertArrayEquals(sampleVec, chunks.get(0).getEmbedding());

        // Update event title - verify embedding is preserved
        serviceWithEmbedding.updateEventMetadata(publishedEvent.getId(), "New Summit Name", null);
        List<DocumentChunk> updatedChunks = serviceWithEmbedding.getChunksByDocumentId(1100L);
        assertEquals("New Summit Name", updatedChunks.get(0).getEventTitle());
        assertTrue(updatedChunks.get(0).hasEmbedding());
        assertArrayEquals(sampleVec, updatedChunks.get(0).getEmbedding());
    }

    @Test
    @DisplayName("Should evict prior chunks if embedding generation throws an exception")
    void shouldEvictChunksIfEmbeddingFails() {
        com.eventease.rag.embedding.EmbeddingService mockEmbedding = org.mockito.Mockito.mock(com.eventease.rag.embedding.EmbeddingService.class);
        org.mockito.Mockito.when(mockEmbedding.embedText("First text"))
                .thenReturn(new float[]{1.0f});
        org.mockito.Mockito.when(mockEmbedding.embedText("Second text"))
                .thenThrow(new RuntimeException("Embedding model out of memory"));

        DocumentIndexService service = new DocumentIndexService(chunker, mockEmbedding);

        EventDocument doc = new EventDocument(publishedEvent, "Doc", DocumentType.FAQ, "First text", null);
        doc.setId(1200L);

        service.indexDocument(doc);
        assertEquals(1, service.getDocumentCount());

        doc.setContent("Second text");
        assertThrows(RuntimeException.class, () -> service.indexDocument(doc));

        // Stale chunks must be evicted
        assertEquals(0, service.getDocumentCount());
        assertTrue(service.getChunksByDocumentId(1200L).isEmpty());
    }
}
