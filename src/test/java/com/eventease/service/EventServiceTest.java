package com.eventease.service;

import com.eventease.entity.Event;
import com.eventease.entity.EventStatus;
import com.eventease.entity.User;
import com.eventease.exception.BusinessRuleException;
import com.eventease.exception.ResourceNotFoundException;
import com.eventease.repository.EventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    private User organizerUser;
    private Event sampleEvent;

    @BeforeEach
    void setUp() {
        organizerUser = new User();
        organizerUser.setId(100L);
        organizerUser.setName("Organizer User");
        organizerUser.setEmail("organizer@eventease.com");

        sampleEvent = new Event();
        sampleEvent.setId(1L);
        sampleEvent.setTitle("Tech Innovation Summit");
        sampleEvent.setDescription("Annual conference");
        sampleEvent.setCategory("Technology");
        sampleEvent.setDate(LocalDate.now().plusDays(10));
        sampleEvent.setStartTime(LocalTime.of(9, 0));
        sampleEvent.setEndTime(LocalTime.of(17, 0));
        sampleEvent.setVenue("Auditorium A");
        sampleEvent.setCapacity(200);
        sampleEvent.setStatus(EventStatus.PENDING);
        sampleEvent.setOrganizer(organizerUser);
        sampleEvent.setCreatedAt(LocalDateTime.now().minusDays(1));
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

    // ==========================================
    // 1. EVENT CREATION TESTS
    // ==========================================

    @Test
    void createEvent_defaultsToPendingStatus() {
        Event newEvent = new Event();
        newEvent.setTitle("New Event");
        newEvent.setStatus(null);

        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event created = eventService.createEvent(newEvent);

        assertNotNull(created);
        assertEquals(EventStatus.PENDING, created.getStatus());
        assertNotNull(created.getCreatedAt());
        verify(eventRepository).save(newEvent);
    }

    @Test
    void createEvent_clientCannotForceCreationAsPublished() {
        Event newEvent = new Event();
        newEvent.setTitle("Attempted Published Event");
        newEvent.setStatus(EventStatus.PUBLISHED);

        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event created = eventService.createEvent(newEvent);

        assertNotNull(created);
        assertEquals(EventStatus.PENDING, created.getStatus(), "createEvent must override status to PENDING");
        verify(eventRepository).save(newEvent);
    }

    // ==========================================
    // 2. VALID STATUS TRANSITIONS
    // ==========================================

    @ParameterizedTest(name = "Valid transition from {0} to {1}")
    @CsvSource({
            "PENDING, APPROVED",
            "PENDING, CANCELLED",
            "APPROVED, PUBLISHED",
            "APPROVED, CANCELLED",
            "APPROVED, PENDING",
            "PUBLISHED, COMPLETED",
            "PUBLISHED, CANCELLED"
    })
    void validTransitions_succeed(EventStatus initialStatus, EventStatus targetStatus) {
        sampleEvent.setStatus(initialStatus);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event updated = eventService.updateEventStatus(1L, targetStatus);

        assertNotNull(updated);
        assertEquals(targetStatus, updated.getStatus());
        verify(eventRepository).save(sampleEvent);
    }

    // ==========================================
    // 3. INVALID STATUS TRANSITIONS
    // ==========================================

    @ParameterizedTest(name = "Invalid transition from {0} to {1}")
    @CsvSource({
            "PENDING, PUBLISHED",
            "PENDING, COMPLETED",
            "APPROVED, COMPLETED",
            "PUBLISHED, PENDING",
            "PUBLISHED, APPROVED",
            "COMPLETED, PUBLISHED",
            "COMPLETED, CANCELLED",
            "COMPLETED, PENDING",
            "COMPLETED, APPROVED",
            "CANCELLED, PUBLISHED",
            "CANCELLED, PENDING",
            "CANCELLED, APPROVED",
            "CANCELLED, COMPLETED",
            "PENDING, PENDING",
            "APPROVED, APPROVED",
            "PUBLISHED, PUBLISHED"
    })
    void invalidTransitions_throwBusinessRuleException(EventStatus initialStatus, EventStatus targetStatus) {
        sampleEvent.setStatus(initialStatus);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                eventService.updateEventStatus(1L, targetStatus)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        String expectedMessage = String.format("Invalid event status transition from %s to %s", initialStatus, targetStatus);
        assertEquals(expectedMessage, ex.getMessage());
        verify(eventRepository, never()).save(any());
    }

    // ==========================================
    // 4. VALIDATION & EDGE CASES
    // ==========================================

    @Test
    void updateEventStatus_nullTargetStatus_throwsBusinessRuleException() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                eventService.updateEventStatus(1L, null)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("Target status is required.", ex.getMessage());
    }

    @Test
    void updateEventStatus_eventNotFound_throwsResourceNotFoundException() {
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                eventService.updateEventStatus(999L, EventStatus.APPROVED)
        );
        assertTrue(ex.getMessage().contains("Event not found with id: 999"));
    }

    @Test
    void updateEvent_doesNotModifyStatus() {
        sampleEvent.setStatus(EventStatus.APPROVED);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event updateDetails = new Event();
        updateDetails.setTitle("Updated Title");
        updateDetails.setStatus(EventStatus.PUBLISHED); // Attempting to alter status via general update

        Event result = eventService.updateEvent(1L, updateDetails);

        assertEquals("Updated Title", result.getTitle());
        assertEquals(EventStatus.APPROVED, result.getStatus(), "Normal updateEvent must not alter status");
    }

    // ==========================================
    // 5. SECURITY & ROLE / OWNERSHIP PERMISSIONS
    // ==========================================

    @Test
    void updateEventStatus_organizerCanChangeOwnEventStatus() {
        setupSecurityContext("organizer@eventease.com", "ROLE_ORGANIZER");
        sampleEvent.setStatus(EventStatus.PENDING);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event updated = eventService.updateEventStatus(1L, EventStatus.APPROVED);

        assertEquals(EventStatus.APPROVED, updated.getStatus());
    }

    @Test
    void updateEventStatus_organizerCannotChangeOtherOrganizerEventStatus() {
        setupSecurityContext("other.organizer@eventease.com", "ROLE_ORGANIZER");
        sampleEvent.setStatus(EventStatus.PENDING);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                eventService.updateEventStatus(1L, EventStatus.APPROVED)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Organizers can only change status for their own events"));
        verify(eventRepository, never()).save(any());
    }

    @Test
    void updateEventStatus_adminCanChangeAnyEventStatus() {
        setupSecurityContext("admin@eventease.com", "ROLE_ADMIN");
        sampleEvent.setStatus(EventStatus.PENDING);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event updated = eventService.updateEventStatus(1L, EventStatus.APPROVED);

        assertEquals(EventStatus.APPROVED, updated.getStatus());
    }

    @Test
    void updateEventStatus_userRoleCannotChangeEventStatus() {
        setupSecurityContext("user@eventease.com", "ROLE_USER");
        sampleEvent.setStatus(EventStatus.PENDING);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                eventService.updateEventStatus(1L, EventStatus.APPROVED)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Users are not authorized to change event status"));
        verify(eventRepository, never()).save(any());
    }

    // ==========================================
    // 6. EVENT SEARCH & MULTI-CRITERIA FILTERING (PHASE 12C)
    // ==========================================

    @Test
    void searchEvents_noFilters_returnsAllEventsViaFindAll() {
        when(eventRepository.findAll()).thenReturn(List.of(sampleEvent));

        List<Event> result = eventService.searchEvents(null, null, (EventStatus) null, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(eventRepository).findAll();
    }

    @Test
    void searchEvents_blankAndAllFilters_returnsAllEventsViaFindAll() {
        when(eventRepository.findAll()).thenReturn(List.of(sampleEvent));

        List<Event> result = eventService.searchEvents("   ", "ALL", "ALL", false);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(eventRepository).findAll();
    }

    @Test
    void searchEvents_withKeyword_invokesSpecificationSearch() {
        when(eventRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(sampleEvent));

        List<Event> result = eventService.searchEvents("Tech Summit", null, (EventStatus) null, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(eventRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class));
    }

    @Test
    void searchEvents_withCategory_invokesSpecificationSearch() {
        when(eventRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(sampleEvent));

        List<Event> result = eventService.searchEvents(null, "Technology", (EventStatus) null, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(eventRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class));
    }

    @Test
    void searchEvents_withStatusEnum_invokesSpecificationSearch() {
        when(eventRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(sampleEvent));

        List<Event> result = eventService.searchEvents(null, null, EventStatus.PUBLISHED, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(eventRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class));
    }

    @Test
    void searchEvents_withValidStatusString_invokesSpecificationSearch() {
        when(eventRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(sampleEvent));

        List<Event> result = eventService.searchEvents(null, null, "published", null);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(eventRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class));
    }

    @Test
    void searchEvents_withInvalidStatusString_throwsBusinessRuleException() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                eventService.searchEvents(null, null, "INVALID_STATUS_VALUE", null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Invalid event status: INVALID_STATUS_VALUE"));
        verify(eventRepository, never()).findAll(any(org.springframework.data.jpa.domain.Specification.class));
    }

    @Test
    void searchEvents_withUpcomingTrue_invokesSpecificationSearch() {
        when(eventRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(sampleEvent));

        List<Event> result = eventService.searchEvents(null, null, (EventStatus) null, true);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(eventRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class));
    }

    @Test
    void searchEvents_withCombinedFilters_invokesSpecificationSearch() {
        when(eventRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(sampleEvent));

        List<Event> result = eventService.searchEvents("Summit", "Technology", EventStatus.PUBLISHED, true);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(eventRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class));
    }

    @Test
    void getAllEvents_returnsRepositoryFindAll() {
        when(eventRepository.findAll()).thenReturn(List.of(sampleEvent));

        List<Event> result = eventService.getAllEvents();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(eventRepository).findAll();
    }

    @Test
    void getEventsByStatus_returnsFilteredStatus() {
        when(eventRepository.findByStatus(EventStatus.APPROVED)).thenReturn(List.of(sampleEvent));

        List<Event> result = eventService.getEventsByStatus(EventStatus.APPROVED);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(eventRepository).findByStatus(EventStatus.APPROVED);
    }

    @Test
    void searchEvents_paginated_unfiltered_success() {
        Page<Event> page = new PageImpl<>(List.of(sampleEvent));
        when(eventRepository.findAll(any(Pageable.class))).thenReturn(page);

        Page<Event> result = eventService.searchEvents(null, null, null, null, 0, 10, "date", "asc");

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        verify(eventRepository).findAll(any(Pageable.class));
    }

    @Test
    void searchEvents_paginated_filtered_success() {
        Page<Event> page = new PageImpl<>(List.of(sampleEvent));
        when(eventRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<Event> result = eventService.searchEvents("Tech", "Conference", "PUBLISHED", true, 1, 20, "title", "desc");

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(eventRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void searchEvents_paginated_negativePage_throwsBadRequest() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                eventService.searchEvents(null, null, null, null, -1, 10, "date", "asc")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Page index must not be less than zero"));
    }

    @Test
    void searchEvents_paginated_zeroSize_throwsBadRequest() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                eventService.searchEvents(null, null, null, null, 0, 0, "date", "asc")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Page size must be between 1 and 50"));
    }

    @Test
    void searchEvents_paginated_excessiveSize_throwsBadRequest() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                eventService.searchEvents(null, null, null, null, 0, 51, "date", "asc")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Page size must be between 1 and 50"));
    }

    @Test
    void searchEvents_paginated_invalidSortBy_throwsBadRequest() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                eventService.searchEvents(null, null, null, null, 0, 10, "unknownField", "asc")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Invalid sort field"));
    }

    @Test
    void searchEvents_paginated_invalidDirection_throwsBadRequest() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                eventService.searchEvents(null, null, null, null, 0, 10, "date", "sideways")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Invalid sort direction"));
    }

    @Test
    void searchEvents_paginated_invalidStatus_throwsBadRequest() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                eventService.searchEvents(null, null, "BOGUS_STATUS", null, 0, 10, "date", "asc")
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Invalid event status"));
    }

    @Test
    void searchEvents_paginated_supportedSortFields() {
        Page<Event> page = new PageImpl<>(List.of(sampleEvent));
        when(eventRepository.findAll(any(Pageable.class))).thenReturn(page);

        for (String field : List.of("date", "title", "category", "createdAt", "capacity", "status", "venue")) {
            Page<Event> result = eventService.searchEvents(null, null, null, null, 0, 10, field, "desc");
            assertNotNull(result);
        }
    }

    // ==========================================
    // 9. RAG INDEX SYNCHRONIZATION TESTS
    // ==========================================

    @Test
    void updateEventStatus_toCompleted_updatesIndexStatus() {
        com.eventease.rag.index.DocumentIndexService mockIndexService = mock(com.eventease.rag.index.DocumentIndexService.class);
        eventService.setDocumentIndexService(mockIndexService);

        sampleEvent.setStatus(EventStatus.PUBLISHED);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));

        eventService.updateEventStatus(1L, EventStatus.COMPLETED);

        verify(mockIndexService).updateEventStatus(1L, EventStatus.COMPLETED);
    }

    @Test
    void updateEventStatus_toCancelled_removesEventFromIndex() {
        com.eventease.rag.index.DocumentIndexService mockIndexService = mock(com.eventease.rag.index.DocumentIndexService.class);
        eventService.setDocumentIndexService(mockIndexService);

        sampleEvent.setStatus(EventStatus.PUBLISHED);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));

        eventService.updateEventStatus(1L, EventStatus.CANCELLED);

        verify(mockIndexService).updateEventStatus(1L, EventStatus.CANCELLED);
        verify(mockIndexService).removeEventDocuments(1L);
    }

    @Test
    void updateEventStatus_toPublished_updatesIndexAndIndexesDocuments() {
        com.eventease.rag.index.DocumentIndexService mockIndexService = mock(com.eventease.rag.index.DocumentIndexService.class);
        com.eventease.repository.EventDocumentRepository mockDocRepo = mock(com.eventease.repository.EventDocumentRepository.class);
        eventService.setDocumentIndexService(mockIndexService);
        eventService.setEventDocumentRepository(mockDocRepo);

        com.eventease.entity.EventDocument doc = new com.eventease.entity.EventDocument();
        doc.setId(55L);
        when(mockDocRepo.findByEventId(1L)).thenReturn(List.of(doc));

        sampleEvent.setStatus(EventStatus.APPROVED);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));

        eventService.updateEventStatus(1L, EventStatus.PUBLISHED);

        verify(mockIndexService).updateEventStatus(1L, EventStatus.PUBLISHED);
        verify(mockIndexService).indexDocument(doc);
    }

    @Test
    void updateEvent_updatesEventMetadataInIndex() {
        com.eventease.rag.index.DocumentIndexService mockIndexService = mock(com.eventease.rag.index.DocumentIndexService.class);
        eventService.setDocumentIndexService(mockIndexService);

        when(eventRepository.findById(1L)).thenReturn(Optional.of(sampleEvent));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> i.getArgument(0));

        Event updateReq = new Event();
        updateReq.setTitle("Updated Title");
        updateReq.setDescription("Updated Desc");

        eventService.updateEvent(1L, updateReq);

        verify(mockIndexService).updateEventMetadata(1L, "Updated Title", EventStatus.PENDING);
    }

    @Test
    void deleteEvent_removesEventDocumentsFromIndex() {
        com.eventease.rag.index.DocumentIndexService mockIndexService = mock(com.eventease.rag.index.DocumentIndexService.class);
        eventService.setDocumentIndexService(mockIndexService);

        when(eventRepository.existsById(1L)).thenReturn(true);

        eventService.deleteEvent(1L);

        verify(mockIndexService).removeEventDocuments(1L);
        verify(eventRepository).deleteById(1L);
    }
}
