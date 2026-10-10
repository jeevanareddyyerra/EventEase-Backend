package com.eventease.controller;

import com.eventease.dto.request.EventDocumentRequest;
import com.eventease.dto.response.EventDocumentResponse;
import com.eventease.entity.EventDocument;
import com.eventease.service.EventDocumentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/event-documents")
public class EventDocumentController {

    private final EventDocumentService eventDocumentService;

    public EventDocumentController(EventDocumentService eventDocumentService) {
        this.eventDocumentService = eventDocumentService;
    }

    @PostMapping
    public ResponseEntity<EventDocumentResponse> createDocument(@Valid @RequestBody EventDocumentRequest request) {
        EventDocument created = eventDocumentService.createDocument(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventDocumentResponse> getDocumentById(@PathVariable Long id) {
        EventDocument document = eventDocumentService.getDocumentById(id);
        return ResponseEntity.ok(toResponse(document));
    }

    @GetMapping
    public ResponseEntity<List<EventDocumentResponse>> getAllDocuments() {
        List<EventDocumentResponse> responses = eventDocumentService.getAllDocuments()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<List<EventDocumentResponse>> getDocumentsByEvent(@PathVariable Long eventId) {
        List<EventDocumentResponse> responses = eventDocumentService.getDocumentsByEvent(eventId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventDocumentResponse> updateDocument(@PathVariable Long id,
                                                                @Valid @RequestBody EventDocumentRequest request) {
        EventDocument updated = eventDocumentService.updateDocument(id, request);
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        eventDocumentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    private EventDocumentResponse toResponse(EventDocument document) {
        Long eventId = document.getEvent() != null ? document.getEvent().getId() : null;
        return new EventDocumentResponse(
                document.getId(),
                eventId,
                document.getTitle(),
                document.getType(),
                document.getContent(),
                document.getFileUrl(),
                document.getCreatedAt()
        );
    }
}
