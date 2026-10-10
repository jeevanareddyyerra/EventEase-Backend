package com.eventease.rag.index;

import com.eventease.entity.DocumentType;
import com.eventease.entity.Event;
import com.eventease.entity.EventDocument;
import com.eventease.entity.EventStatus;
import com.eventease.rag.config.RagProperties;
import com.eventease.repository.EventDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentIndexInitializerTest {

    @Mock
    private EventDocumentRepository eventDocumentRepository;

    @Mock
    private DocumentIndexService documentIndexService;

    private RagProperties ragProperties;
    private DocumentIndexInitializer initializer;

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties(500, 100, true);
        initializer = new DocumentIndexInitializer(eventDocumentRepository, documentIndexService, ragProperties);
    }

    @Test
    @DisplayName("Should query documents and rebuild index when auto-index on startup is enabled")
    void shouldWarmUpIndexOnApplicationReady() {
        Event event = new Event();
        event.setId(1L);
        event.setStatus(EventStatus.PUBLISHED);

        EventDocument doc = new EventDocument(event, "Guidelines", DocumentType.GUIDELINES, "Sample content", null);
        doc.setId(10L);

        when(eventDocumentRepository.findAllWithEvent()).thenReturn(List.of(doc));

        initializer.onApplicationReady();

        verify(eventDocumentRepository).findAllWithEvent();
        verify(documentIndexService).rebuildIndex(List.of(doc));
    }

    @Test
    @DisplayName("Should skip index warmup when auto-index on startup is disabled")
    void shouldSkipWarmUpWhenDisabled() {
        ragProperties.setAutoIndexOnStartup(false);

        initializer.onApplicationReady();

        verifyNoInteractions(eventDocumentRepository);
        verifyNoInteractions(documentIndexService);
    }

    @Test
    @DisplayName("Should gracefully handle repository exceptions without propagating failure")
    void shouldHandleRepositoryExceptionGracefully() {
        when(eventDocumentRepository.findAllWithEvent()).thenThrow(new RuntimeException("Database offline"));

        // Should not throw exception
        initializer.onApplicationReady();

        verify(eventDocumentRepository).findAllWithEvent();
        verifyNoInteractions(documentIndexService);
    }
}
