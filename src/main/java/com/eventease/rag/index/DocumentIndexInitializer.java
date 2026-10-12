package com.eventease.rag.index;

import com.eventease.entity.EventDocument;
import com.eventease.rag.config.RagProperties;
import com.eventease.repository.EventDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Initializes and warms up the in-memory RAG document index upon application startup.
 */
@Component
public class DocumentIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexInitializer.class);

    private final EventDocumentRepository eventDocumentRepository;
    private final DocumentIndexService documentIndexService;
    private final RagProperties ragProperties;

    public DocumentIndexInitializer(
            EventDocumentRepository eventDocumentRepository,
            @org.springframework.context.annotation.Lazy DocumentIndexService documentIndexService,
            RagProperties ragProperties
    ) {
        this.eventDocumentRepository = eventDocumentRepository;
        this.documentIndexService = documentIndexService;
        this.ragProperties = ragProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void onApplicationReady() {
        if (ragProperties == null || !ragProperties.isAutoIndexOnStartup()) {
            log.info("RAG auto-indexing on startup is disabled.");
            return;
        }

        try {
            log.info("Warming up RAG in-memory document index from database...");
            // Use single-query joined fetch to avoid N+1 queries
            List<EventDocument> documents = eventDocumentRepository.findAllWithEvent();
            documentIndexService.rebuildIndex(documents);
            log.info("RAG index warmup complete: {} document(s), {} total chunk(s), {} public chunk(s).",
                    documentIndexService.getDocumentCount(),
                    documentIndexService.getChunkCount(),
                    documentIndexService.getPublicChunks().size());
        } catch (Exception e) {
            log.warn("Failed to warm up RAG document index on startup: {}. Index remains empty until rebuilt.", e.getMessage());
        }
    }
}
