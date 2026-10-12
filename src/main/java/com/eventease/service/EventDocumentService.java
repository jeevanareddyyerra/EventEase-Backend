package com.eventease.service;

import com.eventease.dto.request.EventDocumentRequest;
import com.eventease.entity.Event;
import com.eventease.entity.EventDocument;
import com.eventease.entity.EventStatus;
import com.eventease.entity.User;
import com.eventease.exception.BusinessRuleException;
import com.eventease.exception.ResourceNotFoundException;
import com.eventease.repository.EventDocumentRepository;
import com.eventease.repository.EventRepository;
import com.eventease.repository.UserRepository;
import com.eventease.rag.index.DocumentIndexService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EventDocumentService {

    private final EventDocumentRepository eventDocumentRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final DocumentIndexService documentIndexService;

    @Autowired
    public EventDocumentService(
            EventDocumentRepository eventDocumentRepository,
            EventRepository eventRepository,
            UserRepository userRepository,
            @Lazy DocumentIndexService documentIndexService
    ) {
        this.eventDocumentRepository = eventDocumentRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.documentIndexService = documentIndexService;
    }

    public EventDocumentService(
            EventDocumentRepository eventDocumentRepository,
            EventRepository eventRepository,
            UserRepository userRepository
    ) {
        this(eventDocumentRepository, eventRepository, userRepository, null);
    }

    @Transactional
    public EventDocument createDocument(EventDocumentRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Request cannot be null.", HttpStatus.BAD_REQUEST);
        }

        // 1. Event ID requirement
        if (request.getEventId() == null) {
            throw new BusinessRuleException("Event ID is required.", HttpStatus.BAD_REQUEST);
        }

        // 2. Title & Type validation
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new BusinessRuleException("Title is required and must not be blank.", HttpStatus.BAD_REQUEST);
        }
        if (request.getType() == null) {
            throw new BusinessRuleException("Document type is required.", HttpStatus.BAD_REQUEST);
        }

        // 3. Content and fileUrl validation
        validateContentAndFileUrl(request.getContent(), request.getFileUrl());

        // 4. Event existence
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + request.getEventId()));

        // 5. Authorization
        checkOrganizerOrAdmin(event,
                "Organizers can only create documents for their own events.",
                "Users are not authorized to create event documents.");

        // 6. Event status check (must not be COMPLETED or CANCELLED)
        if (event.getStatus() == EventStatus.COMPLETED || event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleException("Cannot add documents to a " + event.getStatus() + " event.", HttpStatus.BAD_REQUEST);
        }

        // 7. Create entity
        EventDocument document = new EventDocument();
        document.setEvent(event);
        document.setTitle(request.getTitle().trim());
        document.setType(request.getType());
        document.setContent(request.getContent());
        document.setFileUrl(request.getFileUrl());
        document.setCreatedAt(LocalDateTime.now());

        EventDocument saved = eventDocumentRepository.save(document);
        if (documentIndexService != null) {
            documentIndexService.indexDocument(saved);
        }
        return saved;
    }

    @Transactional
    public EventDocument createDocument(EventDocument document) {
        if (document == null) {
            throw new BusinessRuleException("Document cannot be null.", HttpStatus.BAD_REQUEST);
        }
        Long eventId = document.getEvent() != null ? document.getEvent().getId() : null;
        EventDocumentRequest request = new EventDocumentRequest(
                eventId,
                document.getTitle(),
                document.getType(),
                document.getContent(),
                document.getFileUrl()
        );
        return createDocument(request);
    }

    public EventDocument getDocumentById(Long id) {
        return eventDocumentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EventDocument not found with id: " + id));
    }

    public List<EventDocument> getAllDocuments() {
        return eventDocumentRepository.findAll();
    }

    public List<EventDocument> getDocumentsByEvent(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event not found with id: " + eventId);
        }
        return eventDocumentRepository.findByEventId(eventId);
    }

    @Transactional
    public EventDocument updateDocument(Long id, EventDocumentRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Request cannot be null.", HttpStatus.BAD_REQUEST);
        }

        // 1. Document existence
        EventDocument existing = getDocumentById(id);

        // 2. Authorization
        checkOrganizerOrAdmin(existing.getEvent(),
                "Organizers can only update documents for their own events.",
                "Users are not authorized to update event documents.");

        // 3. Event status check
        Event event = existing.getEvent();
        if (event != null && (event.getStatus() == EventStatus.COMPLETED || event.getStatus() == EventStatus.CANCELLED)) {
            throw new BusinessRuleException("Cannot update documents for a " + event.getStatus() + " event.", HttpStatus.BAD_REQUEST);
        }

        // 4. Title & Type validation
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new BusinessRuleException("Title is required and must not be blank.", HttpStatus.BAD_REQUEST);
        }
        if (request.getType() == null) {
            throw new BusinessRuleException("Document type is required.", HttpStatus.BAD_REQUEST);
        }

        // 5. Content & fileUrl validation
        validateContentAndFileUrl(request.getContent(), request.getFileUrl());

        // 6. Update fields (preserving createdAt and event relationship)
        existing.setTitle(request.getTitle().trim());
        existing.setType(request.getType());
        existing.setContent(request.getContent());
        existing.setFileUrl(request.getFileUrl());

        EventDocument saved = eventDocumentRepository.save(existing);
        if (documentIndexService != null) {
            documentIndexService.indexDocument(saved);
        }
        return saved;
    }

    @Transactional
    public EventDocument updateDocument(Long id, EventDocument document) {
        if (document == null) {
            throw new BusinessRuleException("Document cannot be null.", HttpStatus.BAD_REQUEST);
        }
        Long eventId = document.getEvent() != null ? document.getEvent().getId() : null;
        EventDocumentRequest request = new EventDocumentRequest(
                eventId,
                document.getTitle(),
                document.getType(),
                document.getContent(),
                document.getFileUrl()
        );
        return updateDocument(id, request);
    }

    @Transactional
    public void deleteDocument(Long id) {
        // 1. Document existence
        EventDocument existing = getDocumentById(id);

        // 2. Authorization
        checkOrganizerOrAdmin(existing.getEvent(),
                "Organizers can only delete documents for their own events.",
                "Users are not authorized to delete event documents.");

        // 3. Event status check (cannot delete once event is COMPLETED)
        if (existing.getEvent() != null && existing.getEvent().getStatus() == EventStatus.COMPLETED) {
            throw new BusinessRuleException("Documents cannot be deleted once the event is COMPLETED.", HttpStatus.BAD_REQUEST);
        }

        // 4. Delete document
        eventDocumentRepository.delete(existing);
        if (documentIndexService != null) {
            documentIndexService.removeDocument(id);
        }
    }

    private void validateContentAndFileUrl(String content, String fileUrl) {
        boolean hasContent = content != null && !content.trim().isEmpty();
        boolean hasFileUrl = fileUrl != null && !fileUrl.trim().isEmpty();

        if (content != null && content.trim().isEmpty()) {
            throw new BusinessRuleException("Document content must not be blank if provided.", HttpStatus.BAD_REQUEST);
        }

        if (!hasContent && !hasFileUrl) {
            throw new BusinessRuleException("At least one of content or fileUrl must be provided.", HttpStatus.BAD_REQUEST);
        }
    }

    private void checkOrganizerOrAdmin(Event event, String organizerMismatchMsg, String genericForbiddenMsg) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User authUser = getAuthenticatedUser();

        if (auth != null && hasRole(auth, "ADMIN")) {
            return;
        }

        if (auth != null && hasRole(auth, "ORGANIZER")) {
            if (event != null && event.getOrganizer() != null && !isOrganizerOwner(event.getOrganizer(), authUser, auth)) {
                throw new BusinessRuleException(organizerMismatchMsg, HttpStatus.FORBIDDEN);
            }
            return;
        }

        // USER or unprivileged
        throw new BusinessRuleException(genericForbiddenMsg, HttpStatus.FORBIDDEN);
    }

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String && "anonymousUser".equals(auth.getPrincipal()))) {
            return userRepository.findByEmail(auth.getName()).orElse(null);
        }
        return null;
    }

    private boolean hasRole(Authentication auth, String role) {
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role) || a.getAuthority().equals("ROLE_" + role));
    }

    private boolean isOrganizerOwner(User organizer, User authUser, Authentication auth) {
        if (organizer == null) return false;
        if (authUser != null) {
            if (organizer.getId() != null && authUser.getId() != null && organizer.getId().equals(authUser.getId())) {
                return true;
            }
            if (organizer.getEmail() != null && organizer.getEmail().equalsIgnoreCase(authUser.getEmail())) {
                return true;
            }
        }
        if (auth != null && organizer.getEmail() != null && organizer.getEmail().equalsIgnoreCase(auth.getName())) {
            return true;
        }
        return false;
    }
}
