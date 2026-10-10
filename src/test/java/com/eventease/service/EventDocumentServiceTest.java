package com.eventease.service;

import com.eventease.dto.request.EventDocumentRequest;
import com.eventease.entity.*;
import com.eventease.exception.BusinessRuleException;
import com.eventease.exception.ResourceNotFoundException;
import com.eventease.repository.EventDocumentRepository;
import com.eventease.repository.EventRepository;
import com.eventease.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventDocumentServiceTest {

    @Mock
    private EventDocumentRepository eventDocumentRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.eventease.rag.index.DocumentIndexService documentIndexService;

    @InjectMocks
    private EventDocumentService eventDocumentService;

    private User normalUser;
    private User organizer1;
    private User organizer2;
    private User adminUser;
    private Event publishedEvent;
    private Event completedEvent;
    private Event cancelledEvent;
    private EventDocument existingDoc;
    private EventDocument docOnCompletedEvent;

    @BeforeEach
    void setUp() {
        normalUser = new User();
        normalUser.setId(1L);
        normalUser.setName("Normal User");
        normalUser.setEmail("user@example.com");
        normalUser.setRole(Role.USER);

        organizer1 = new User();
        organizer1.setId(2L);
        organizer1.setName("Organizer One");
        organizer1.setEmail("organizer1@example.com");
        organizer1.setRole(Role.ORGANIZER);

        organizer2 = new User();
        organizer2.setId(3L);
        organizer2.setName("Organizer Two");
        organizer2.setEmail("organizer2@example.com");
        organizer2.setRole(Role.ORGANIZER);

        adminUser = new User();
        adminUser.setId(4L);
        adminUser.setName("Admin User");
        adminUser.setEmail("admin@example.com");
        adminUser.setRole(Role.ADMIN);

        publishedEvent = new Event();
        publishedEvent.setId(10L);
        publishedEvent.setTitle("Tech Symposium");
        publishedEvent.setStatus(EventStatus.PUBLISHED);
        publishedEvent.setOrganizer(organizer1);

        completedEvent = new Event();
        completedEvent.setId(11L);
        completedEvent.setTitle("Past Symposium");
        completedEvent.setStatus(EventStatus.COMPLETED);
        completedEvent.setOrganizer(organizer1);

        cancelledEvent = new Event();
        cancelledEvent.setId(12L);
        cancelledEvent.setTitle("Cancelled Symposium");
        cancelledEvent.setStatus(EventStatus.CANCELLED);
        cancelledEvent.setOrganizer(organizer1);

        existingDoc = new EventDocument();
        existingDoc.setId(100L);
        existingDoc.setEvent(publishedEvent);
        existingDoc.setTitle("Event FAQ");
        existingDoc.setType(DocumentType.FAQ);
        existingDoc.setContent("Existing content");
        existingDoc.setCreatedAt(LocalDateTime.now().minusDays(1));

        docOnCompletedEvent = new EventDocument();
        docOnCompletedEvent.setId(101L);
        docOnCompletedEvent.setEvent(completedEvent);
        docOnCompletedEvent.setTitle("Completed FAQ");
        docOnCompletedEvent.setType(DocumentType.FAQ);
        docOnCompletedEvent.setContent("Past content");
        docOnCompletedEvent.setCreatedAt(LocalDateTime.now().minusDays(5));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setupSecurityContext(String email, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                email,
                null,
                Collections.singletonList(new SimpleGrantedAuthority(role))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // 1. create valid content document
    @Test
    void createDocument_validContent_success() {
        setupSecurityContext("organizer1@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer1@example.com")).thenReturn(Optional.of(organizer1));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(eventDocumentRepository.save(any(EventDocument.class))).thenAnswer(i -> i.getArgument(0));

        EventDocumentRequest request = new EventDocumentRequest(10L, "Event Rules", DocumentType.RULES, "Detailed rules content", null);
        EventDocument created = eventDocumentService.createDocument(request);

        assertNotNull(created);
        assertEquals("Event Rules", created.getTitle());
        assertEquals(DocumentType.RULES, created.getType());
        assertEquals("Detailed rules content", created.getContent());
        assertNull(created.getFileUrl());
        assertNotNull(created.getCreatedAt());
        verify(eventDocumentRepository).save(any(EventDocument.class));
    }

    // 2. create valid fileUrl document
    @Test
    void createDocument_validFileUrl_success() {
        setupSecurityContext("organizer1@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer1@example.com")).thenReturn(Optional.of(organizer1));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(eventDocumentRepository.save(any(EventDocument.class))).thenAnswer(i -> i.getArgument(0));

        EventDocumentRequest request = new EventDocumentRequest(10L, "Event Guidelines PDF", DocumentType.GUIDELINES, null, "https://example.com/docs/guidelines.pdf");
        EventDocument created = eventDocumentService.createDocument(request);

        assertNotNull(created);
        assertEquals("Event Guidelines PDF", created.getTitle());
        assertEquals(DocumentType.GUIDELINES, created.getType());
        assertNull(created.getContent());
        assertEquals("https://example.com/docs/guidelines.pdf", created.getFileUrl());
        assertNotNull(created.getCreatedAt());
        verify(eventDocumentRepository).save(any(EventDocument.class));
    }

    // 3. reject missing content and fileUrl
    @Test
    void createDocument_missingContentAndFileUrl_throwsBusinessRuleException() {
        EventDocumentRequest request = new EventDocumentRequest(10L, "Empty Doc", DocumentType.FAQ, null, null);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> eventDocumentService.createDocument(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("At least one of content or fileUrl must be provided"));
    }

    // 4. reject blank content when fileUrl absent
    @Test
    void createDocument_blankContentWhenFileUrlAbsent_throwsBusinessRuleException() {
        EventDocumentRequest request = new EventDocumentRequest(10L, "Blank Doc", DocumentType.FAQ, "   ", null);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> eventDocumentService.createDocument(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("blank"));
    }

    // 5. nonexistent event
    @Test
    void createDocument_nonexistentEvent_throwsResourceNotFoundException() {
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());

        EventDocumentRequest request = new EventDocumentRequest(999L, "Rules", DocumentType.RULES, "Valid content", null);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> eventDocumentService.createDocument(request));
        assertTrue(ex.getMessage().contains("Event not found with id: 999"));
    }

    // 6. completed event creation rejected
    @Test
    void createDocument_completedEvent_throwsBusinessRuleException() {
        setupSecurityContext("organizer1@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer1@example.com")).thenReturn(Optional.of(organizer1));
        when(eventRepository.findById(11L)).thenReturn(Optional.of(completedEvent));

        EventDocumentRequest request = new EventDocumentRequest(11L, "Rules", DocumentType.RULES, "Valid content", null);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> eventDocumentService.createDocument(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("COMPLETED"));
    }

    // 7. cancelled event creation rejected
    @Test
    void createDocument_cancelledEvent_throwsBusinessRuleException() {
        setupSecurityContext("organizer1@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer1@example.com")).thenReturn(Optional.of(organizer1));
        when(eventRepository.findById(12L)).thenReturn(Optional.of(cancelledEvent));

        EventDocumentRequest request = new EventDocumentRequest(12L, "Rules", DocumentType.RULES, "Valid content", null);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> eventDocumentService.createDocument(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("CANCELLED"));
    }

    // 8. USER create forbidden
    @Test
    void createDocument_asUser_throwsForbidden() {
        setupSecurityContext("user@example.com", "ROLE_USER");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));

        EventDocumentRequest request = new EventDocumentRequest(10L, "Rules", DocumentType.RULES, "Valid content", null);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> eventDocumentService.createDocument(request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Users are not authorized"));
    }

    // 9. organizer creates for own event
    @Test
    void createDocument_organizerOwnEvent_success() {
        setupSecurityContext("organizer1@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer1@example.com")).thenReturn(Optional.of(organizer1));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(eventDocumentRepository.save(any(EventDocument.class))).thenAnswer(i -> i.getArgument(0));

        EventDocumentRequest request = new EventDocumentRequest(10L, "FAQ", DocumentType.FAQ, "Valid FAQ content", null);
        EventDocument created = eventDocumentService.createDocument(request);

        assertNotNull(created);
        assertEquals("FAQ", created.getTitle());
    }

    // 10. organizer creates for another organizer's event forbidden
    @Test
    void createDocument_organizerAnotherEvent_throwsForbidden() {
        setupSecurityContext("organizer2@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer2@example.com")).thenReturn(Optional.of(organizer2));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));

        EventDocumentRequest request = new EventDocumentRequest(10L, "FAQ", DocumentType.FAQ, "Valid FAQ content", null);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> eventDocumentService.createDocument(request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Organizers can only create documents for their own events"));
    }

    // 11. ADMIN creates for any event
    @Test
    void createDocument_adminAnyEvent_success() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(eventDocumentRepository.save(any(EventDocument.class))).thenAnswer(i -> i.getArgument(0));

        EventDocumentRequest request = new EventDocumentRequest(10L, "Admin Note", DocumentType.RULES, "Admin created rule", null);
        EventDocument created = eventDocumentService.createDocument(request);

        assertNotNull(created);
        assertEquals("Admin Note", created.getTitle());
    }

    // 12. update valid document
    @Test
    void updateDocument_validDocument_success() {
        setupSecurityContext("organizer1@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer1@example.com")).thenReturn(Optional.of(organizer1));
        when(eventDocumentRepository.findById(100L)).thenReturn(Optional.of(existingDoc));
        when(eventDocumentRepository.save(any(EventDocument.class))).thenAnswer(i -> i.getArgument(0));

        LocalDateTime originalCreatedAt = existingDoc.getCreatedAt();

        EventDocumentRequest updateRequest = new EventDocumentRequest(10L, "Updated FAQ Title", DocumentType.GUIDELINES, "Updated content body", "https://example.com/updated.pdf");
        EventDocument updated = eventDocumentService.updateDocument(100L, updateRequest);

        assertNotNull(updated);
        assertEquals("Updated FAQ Title", updated.getTitle());
        assertEquals(DocumentType.GUIDELINES, updated.getType());
        assertEquals("Updated content body", updated.getContent());
        assertEquals("https://example.com/updated.pdf", updated.getFileUrl());
        assertEquals(originalCreatedAt, updated.getCreatedAt()); // preserved
    }

    // 13. USER update forbidden
    @Test
    void updateDocument_asUser_throwsForbidden() {
        setupSecurityContext("user@example.com", "ROLE_USER");
        when(eventDocumentRepository.findById(100L)).thenReturn(Optional.of(existingDoc));

        EventDocumentRequest updateRequest = new EventDocumentRequest(10L, "New Title", DocumentType.FAQ, "New content", null);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> eventDocumentService.updateDocument(100L, updateRequest));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Users are not authorized"));
    }

    // 14. organizer update own event
    @Test
    void updateDocument_organizerOwnEvent_success() {
        setupSecurityContext("organizer1@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer1@example.com")).thenReturn(Optional.of(organizer1));
        when(eventDocumentRepository.findById(100L)).thenReturn(Optional.of(existingDoc));
        when(eventDocumentRepository.save(any(EventDocument.class))).thenAnswer(i -> i.getArgument(0));

        EventDocumentRequest updateRequest = new EventDocumentRequest(10L, "Updated Title", DocumentType.FAQ, "Updated content", null);
        EventDocument updated = eventDocumentService.updateDocument(100L, updateRequest);

        assertNotNull(updated);
        assertEquals("Updated Title", updated.getTitle());
    }

    // 15. organizer update another event forbidden
    @Test
    void updateDocument_organizerAnotherEvent_throwsForbidden() {
        setupSecurityContext("organizer2@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer2@example.com")).thenReturn(Optional.of(organizer2));
        when(eventDocumentRepository.findById(100L)).thenReturn(Optional.of(existingDoc));

        EventDocumentRequest updateRequest = new EventDocumentRequest(10L, "Updated Title", DocumentType.FAQ, "Updated content", null);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> eventDocumentService.updateDocument(100L, updateRequest));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Organizers can only update documents for their own events"));
    }

    // 16. completed event update rejected
    @Test
    void updateDocument_completedEvent_throwsBusinessRuleException() {
        setupSecurityContext("organizer1@example.com", "ROLE_ORGANIZER");
        when(eventDocumentRepository.findById(101L)).thenReturn(Optional.of(docOnCompletedEvent));

        EventDocumentRequest updateRequest = new EventDocumentRequest(11L, "Try Update", DocumentType.FAQ, "New content", null);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> eventDocumentService.updateDocument(101L, updateRequest));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("COMPLETED"));
    }

    // 17. delete valid document
    @Test
    void deleteDocument_validDocument_success() {
        setupSecurityContext("organizer1@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer1@example.com")).thenReturn(Optional.of(organizer1));
        when(eventDocumentRepository.findById(100L)).thenReturn(Optional.of(existingDoc));

        eventDocumentService.deleteDocument(100L);

        verify(eventDocumentRepository).delete(existingDoc);
    }

    // 18. USER delete forbidden
    @Test
    void deleteDocument_asUser_throwsForbidden() {
        setupSecurityContext("user@example.com", "ROLE_USER");
        when(eventDocumentRepository.findById(100L)).thenReturn(Optional.of(existingDoc));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> eventDocumentService.deleteDocument(100L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Users are not authorized"));
    }

    // 19. organizer delete own document
    @Test
    void deleteDocument_organizerOwnDocument_success() {
        setupSecurityContext("organizer1@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer1@example.com")).thenReturn(Optional.of(organizer1));
        when(eventDocumentRepository.findById(100L)).thenReturn(Optional.of(existingDoc));

        eventDocumentService.deleteDocument(100L);

        verify(eventDocumentRepository).delete(existingDoc);
    }

    // 20. organizer delete another event's document forbidden
    @Test
    void deleteDocument_organizerAnotherEventDocument_throwsForbidden() {
        setupSecurityContext("organizer2@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer2@example.com")).thenReturn(Optional.of(organizer2));
        when(eventDocumentRepository.findById(100L)).thenReturn(Optional.of(existingDoc));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> eventDocumentService.deleteDocument(100L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Organizers can only delete documents for their own events"));
    }

    // 21. completed event delete rejected
    @Test
    void deleteDocument_completedEvent_throwsBusinessRuleException() {
        setupSecurityContext("organizer1@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer1@example.com")).thenReturn(Optional.of(organizer1));
        when(eventDocumentRepository.findById(101L)).thenReturn(Optional.of(docOnCompletedEvent));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> eventDocumentService.deleteDocument(101L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("COMPLETED"));
    }

    // 22. retrieval by event
    @Test
    void getDocumentsByEvent_success() {
        when(eventRepository.existsById(10L)).thenReturn(true);
        when(eventDocumentRepository.findByEventId(10L)).thenReturn(List.of(existingDoc));

        List<EventDocument> docs = eventDocumentService.getDocumentsByEvent(10L);

        assertNotNull(docs);
        assertEquals(1, docs.size());
        assertEquals("Event FAQ", docs.get(0).getTitle());
    }

    // 23. nonexistent document
    @Test
    void getDocumentById_nonexistent_throwsResourceNotFoundException() {
        when(eventDocumentRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> eventDocumentService.getDocumentById(999L));
        assertTrue(ex.getMessage().contains("EventDocument not found with id: 999"));
    }

    // 24. nonexistent event
    @Test
    void getDocumentsByEvent_nonexistentEvent_throwsResourceNotFoundException() {
        when(eventRepository.existsById(999L)).thenReturn(false);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> eventDocumentService.getDocumentsByEvent(999L));
        assertTrue(ex.getMessage().contains("Event not found with id: 999"));
    }

    // 25. indexing invoked on create
    @Test
    void createDocument_triggersIndexing() {
        setupSecurityContext("organizer1@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer1@example.com")).thenReturn(Optional.of(organizer1));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(eventDocumentRepository.save(any(EventDocument.class))).thenAnswer(i -> i.getArgument(0));

        EventDocumentRequest request = new EventDocumentRequest(10L, "Event Rules", DocumentType.RULES, "Detailed rules content", null);
        EventDocument created = eventDocumentService.createDocument(request);

        assertNotNull(created);
        verify(documentIndexService).indexDocument(created);
    }

    // 26. indexing invoked on update
    @Test
    void updateDocument_triggersIndexing() {
        setupSecurityContext("organizer1@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer1@example.com")).thenReturn(Optional.of(organizer1));
        when(eventDocumentRepository.findById(100L)).thenReturn(Optional.of(existingDoc));
        when(eventDocumentRepository.save(any(EventDocument.class))).thenAnswer(i -> i.getArgument(0));

        EventDocumentRequest request = new EventDocumentRequest(10L, "Updated Rules", DocumentType.RULES, "Updated rules content", null);
        EventDocument updated = eventDocumentService.updateDocument(100L, request);

        assertNotNull(updated);
        verify(documentIndexService).indexDocument(updated);
    }

    // 27. removal invoked on delete
    @Test
    void deleteDocument_triggersRemovalFromIndex() {
        setupSecurityContext("organizer1@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer1@example.com")).thenReturn(Optional.of(organizer1));
        when(eventDocumentRepository.findById(100L)).thenReturn(Optional.of(existingDoc));

        eventDocumentService.deleteDocument(100L);

        verify(eventDocumentRepository).delete(existingDoc);
        verify(documentIndexService).removeDocument(100L);
    }
}
