package com.eventease.rag.index;

import com.eventease.entity.DocumentType;
import com.eventease.entity.Event;
import com.eventease.entity.EventDocument;
import com.eventease.entity.EventStatus;
import com.eventease.rag.chunking.DocumentChunker;
import com.eventease.rag.embedding.EmbeddingService;
import com.eventease.rag.model.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * In-memory indexing service that manages text chunks and embedding vectors derived from EventDocument entities.
 * <p>
 * Thread-safe with ReentrantReadWriteLock guaranteeing consistent reads, atomic batch rebuilds,
 * safe status transitions, and failure isolation.
 */
@Service
public class DocumentIndexService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexService.class);

    private final DocumentChunker documentChunker;
    private final EmbeddingService embeddingService;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    // Thread-safe map of Document ID -> List of DocumentChunk
    private final ConcurrentMap<Long, List<DocumentChunk>> documentChunksMap = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public DocumentIndexService(DocumentChunker documentChunker, EmbeddingService embeddingService) {
        this.documentChunker = documentChunker != null ? documentChunker : new DocumentChunker();
        this.embeddingService = embeddingService;
    }

    public DocumentIndexService(DocumentChunker documentChunker) {
        this(documentChunker, null);
    }

    /**
     * Chunks, embeds, and indexes the given EventDocument.
     * Existing chunks for this document are atomically replaced, preventing stale chunks.
     * If indexing or embedding fails, any prior chunks are evicted to prevent stale data from being treated as current.
     *
     * @param document The EventDocument entity to index
     */
    public void indexDocument(EventDocument document) {
        if (document == null || document.getId() == null) {
            return;
        }

        Long docId = document.getId();
        rwLock.writeLock().lock();
        try {
            String content = document.getContent();

            // If content is null or blank, remove any prior chunks and exit
            if (content == null || content.trim().isEmpty()) {
                removeDocumentInternal(docId);
                return;
            }

            Event event = document.getEvent();
            Long eventId = event != null ? event.getId() : null;
            String eventTitle = event != null ? event.getTitle() : "Unknown Event";
            EventStatus eventStatus = event != null ? event.getStatus() : EventStatus.PENDING;

            List<String> rawChunks = documentChunker.chunkText(content);
            if (rawChunks.isEmpty()) {
                removeDocumentInternal(docId);
                return;
            }

            List<DocumentChunk> chunks = new ArrayList<>(rawChunks.size());
            LocalDateTime now = LocalDateTime.now();

            for (int i = 0; i < rawChunks.size(); i++) {
                String chunkText = rawChunks.get(i);
                String stableId = DocumentChunk.buildStableId(eventId, docId, i);

                float[] embedding = null;
                if (embeddingService != null) {
                    embedding = embeddingService.embedText(chunkText);
                }

                DocumentChunk chunk = new DocumentChunk(
                        stableId,
                        eventId,
                        eventTitle,
                        eventStatus,
                        docId,
                        document.getTitle(),
                        document.getType(),
                        i,
                        chunkText,
                        now,
                        embedding
                );
                chunks.add(chunk);
            }

            // Replace previous chunks
            documentChunksMap.put(docId, Collections.unmodifiableList(chunks));
            log.debug("Indexed document #{} (event #{}) into {} chunk(s). Status: {}", docId, eventId, chunks.size(), eventStatus);
        } catch (Exception e) {
            // Failure safety: evict prior chunks to ensure failed indexing never leaves stale chunks active
            removeDocumentInternal(docId);
            log.error("Failed to index document #{}. Evicted any prior chunks to prevent stale index state: {}", docId, e.getMessage());
            throw e;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Removes all indexed chunks associated with a specific Document ID.
     *
     * @param documentId Document ID to remove
     */
    public void removeDocument(Long documentId) {
        if (documentId == null) return;
        rwLock.writeLock().lock();
        try {
            removeDocumentInternal(documentId);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void removeDocumentInternal(Long documentId) {
        if (documentId == null) return;
        List<DocumentChunk> removed = documentChunksMap.remove(documentId);
        if (removed != null && !removed.isEmpty()) {
            log.debug("Removed {} chunk(s) for document #{}", removed.size(), documentId);
        }
    }

    /**
     * Removes all indexed chunks associated with an Event ID.
     *
     * @param eventId Event ID whose document chunks should be evicted
     */
    public void removeEventDocuments(Long eventId) {
        if (eventId == null) return;

        rwLock.writeLock().lock();
        try {
            List<Long> docIdsToRemove = documentChunksMap.entrySet().stream()
                    .filter(entry -> entry.getValue().stream().anyMatch(c -> eventId.equals(c.getEventId())))
                    .map(Map.Entry::getKey)
                    .toList();

            for (Long docId : docIdsToRemove) {
                documentChunksMap.remove(docId);
            }

            if (!docIdsToRemove.isEmpty()) {
                log.debug("Removed documents {} for event #{}", docIdsToRemove, eventId);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Updates the status on all chunks associated with an event.
     * Ensures immediate visibility change when an event transitions to/from PUBLISHED.
     * Preserves existing embeddings without recomputing them.
     *
     * @param eventId Event ID to update
     * @param newStatus New EventStatus
     */
    public void updateEventStatus(Long eventId, EventStatus newStatus) {
        updateEventMetadata(eventId, null, newStatus);
    }

    /**
     * Updates event metadata (title and/or status) on all chunks associated with an event.
     * Preserves existing embeddings.
     *
     * @param eventId Event ID to update
     * @param newTitle New event title (or null to retain)
     * @param newStatus New event status (or null to retain)
     */
    public void updateEventMetadata(Long eventId, String newTitle, EventStatus newStatus) {
        if (eventId == null || (newTitle == null && newStatus == null)) return;

        rwLock.writeLock().lock();
        try {
            for (Map.Entry<Long, List<DocumentChunk>> entry : documentChunksMap.entrySet()) {
                List<DocumentChunk> chunks = entry.getValue();
                boolean matches = chunks.stream().anyMatch(c -> eventId.equals(c.getEventId()));
                if (matches) {
                    List<DocumentChunk> updated = chunks.stream()
                            .map(c -> eventId.equals(c.getEventId()) ? c.withEventMetadata(newTitle, newStatus) : c)
                            .toList();
                    documentChunksMap.put(entry.getKey(), Collections.unmodifiableList(updated));
                }
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Rebuilds the index from a collection of documents.
     * Individual document indexing failures are isolated and do not corrupt the overall index.
     * Uses write lock for atomic swap so readers never observe an empty or inconsistent index.
     * Retains any newer updates indexed while rebuild was executing.
     *
     * @param documents Collection of EventDocuments to index
     */
    public void rebuildIndex(Collection<EventDocument> documents) {
        if (documents == null) {
            clear();
            return;
        }

        LocalDateTime rebuildStartTime = LocalDateTime.now();
        ConcurrentMap<Long, List<DocumentChunk>> newMap = new ConcurrentHashMap<>();
        int successCount = 0;
        int failureCount = 0;

        for (EventDocument doc : documents) {
            if (doc == null || doc.getId() == null) continue;
            try {
                String content = doc.getContent();
                if (content != null && !content.trim().isEmpty()) {
                    Event event = doc.getEvent();
                    Long eventId = event != null ? event.getId() : null;
                    String eventTitle = event != null ? event.getTitle() : "Unknown Event";
                    EventStatus eventStatus = event != null ? event.getStatus() : EventStatus.PENDING;

                    List<String> rawChunks = documentChunker.chunkText(content);
                    if (!rawChunks.isEmpty()) {
                        List<DocumentChunk> chunks = new ArrayList<>(rawChunks.size());
                        LocalDateTime now = LocalDateTime.now();
                        for (int i = 0; i < rawChunks.size(); i++) {
                            String chunkText = rawChunks.get(i);
                            float[] embedding = null;
                            if (embeddingService != null) {
                                embedding = embeddingService.embedText(chunkText);
                            }

                            chunks.add(new DocumentChunk(
                                    DocumentChunk.buildStableId(eventId, doc.getId(), i),
                                    eventId,
                                    eventTitle,
                                    eventStatus,
                                    doc.getId(),
                                    doc.getTitle(),
                                    doc.getType(),
                                    i,
                                    chunkText,
                                    now,
                                    embedding
                            ));
                        }
                        newMap.put(doc.getId(), Collections.unmodifiableList(chunks));
                        successCount++;
                    }
                }
            } catch (Exception e) {
                failureCount++;
                log.warn("Failed to index document #{} during index rebuild: {}", doc.getId(), e.getMessage());
            }
        }

        // Atomically swap under write lock
        rwLock.writeLock().lock();
        try {
            // Retain any newer concurrent updates that occurred while rebuild was chunking
            for (Map.Entry<Long, List<DocumentChunk>> entry : documentChunksMap.entrySet()) {
                List<DocumentChunk> existingChunks = entry.getValue();
                if (existingChunks != null && !existingChunks.isEmpty()) {
                    if (existingChunks.get(0).getIndexedAt().isAfter(rebuildStartTime)) {
                        newMap.put(entry.getKey(), existingChunks);
                    }
                }
            }
            documentChunksMap.clear();
            documentChunksMap.putAll(newMap);
        } finally {
            rwLock.writeLock().unlock();
        }

        log.info("RAG Index rebuild completed. Total indexed documents: {}, chunks: {}, failures: {}",
                successCount, getChunkCount(), failureCount);
    }

    /**
     * Returns all chunks across all indexed documents.
     */
    public List<DocumentChunk> getAllChunks() {
        rwLock.readLock().lock();
        try {
            return documentChunksMap.values().stream()
                    .flatMap(List::stream)
                    .toList();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns only chunks associated with PUBLISHED events.
     * Unauthorized, pending, or cancelled event content is strictly excluded from public retrieval.
     */
    public List<DocumentChunk> getPublicChunks() {
        rwLock.readLock().lock();
        try {
            return documentChunksMap.values().stream()
                    .flatMap(List::stream)
                    .filter(DocumentChunk::isPubliclyVisible)
                    .toList();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns all chunks for a specific document ID.
     */
    public List<DocumentChunk> getChunksByDocumentId(Long documentId) {
        if (documentId == null) return Collections.emptyList();
        rwLock.readLock().lock();
        try {
            return documentChunksMap.getOrDefault(documentId, Collections.emptyList());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns all chunks for a specific event ID.
     */
    public List<DocumentChunk> getChunksByEventId(Long eventId) {
        if (eventId == null) return Collections.emptyList();
        rwLock.readLock().lock();
        try {
            return documentChunksMap.values().stream()
                    .flatMap(List::stream)
                    .filter(chunk -> eventId.equals(chunk.getEventId()))
                    .toList();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns public chunks for a specific event ID and document type.
     */
    public List<DocumentChunk> getPublicChunksByEventAndType(Long eventId, DocumentType type) {
        if (eventId == null) return Collections.emptyList();
        rwLock.readLock().lock();
        try {
            return getPublicChunks().stream()
                    .filter(chunk -> eventId.equals(chunk.getEventId()) && (type == null || type == chunk.getDocumentType()))
                    .toList();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Total number of indexed chunks across all documents.
     */
    public int getChunkCount() {
        rwLock.readLock().lock();
        try {
            return documentChunksMap.values().stream().mapToInt(List::size).sum();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Number of distinct documents currently indexed.
     */
    public int getDocumentCount() {
        rwLock.readLock().lock();
        try {
            return documentChunksMap.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Clears all indexed chunks.
     */
    public void clear() {
        rwLock.writeLock().lock();
        try {
            documentChunksMap.clear();
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
