package com.eventease.controller;

import com.eventease.dto.response.RegistrationResponse;
import com.eventease.entity.*;
import com.eventease.service.EventService;
import com.eventease.service.RegistrationService;
import com.eventease.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationControllerTest {

    @Mock
    private RegistrationService registrationService;

    @Mock
    private UserService userService;

    @Mock
    private EventService eventService;

    @InjectMocks
    private RegistrationController registrationController;

    private User sampleUser;
    private Event sampleEvent;
    private Registration sampleRegistration;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(10L);
        sampleUser.setName("Jane Doe");
        sampleUser.setEmail("jane@example.com");

        sampleEvent = new Event();
        sampleEvent.setId(20L);
        sampleEvent.setTitle("Hackathon 2026");
        sampleEvent.setDate(LocalDate.of(2026, 11, 15));
        sampleEvent.setStartTime(LocalTime.of(9, 0));
        sampleEvent.setVenue("Innovation Lab");
        sampleEvent.setStatus(EventStatus.PUBLISHED);

        sampleRegistration = new Registration();
        sampleRegistration.setId(100L);
        sampleRegistration.setUser(sampleUser);
        sampleRegistration.setEvent(sampleEvent);
        sampleRegistration.setType(RegistrationType.PARTICIPANT);
        sampleRegistration.setStatus(RegistrationStatus.REGISTERED);
        sampleRegistration.setRegisteredAt(LocalDateTime.of(2026, 10, 1, 12, 0));
    }

    @Test
    @DisplayName("getRegistrationById maps enriched event fields correctly into response")
    void testGetRegistrationByIdMapsEnrichedFields() {
        when(registrationService.getRegistrationById(100L)).thenReturn(sampleRegistration);

        ResponseEntity<RegistrationResponse> responseEntity = registrationController.getRegistrationById(100L);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        RegistrationResponse body = responseEntity.getBody();
        assertNotNull(body);

        assertEquals(100L, body.getId());
        assertEquals(10L, body.getUserId());
        assertEquals("Jane Doe", body.getUserName());
        assertEquals(20L, body.getEventId());
        assertEquals("Hackathon 2026", body.getEventTitle());
        assertEquals(LocalDate.of(2026, 11, 15), body.getEventDate());
        assertEquals(LocalTime.of(9, 0), body.getEventStartTime());
        assertEquals("Innovation Lab", body.getEventVenue());
        assertEquals(EventStatus.PUBLISHED, body.getEventStatus());
        assertEquals(RegistrationType.PARTICIPANT, body.getType());
        assertEquals(RegistrationStatus.REGISTERED, body.getStatus());
        assertEquals(LocalDateTime.of(2026, 10, 1, 12, 0), body.getRegisteredAt());
    }

    @Test
    @DisplayName("getRegistrationsByUser returns list of enriched RegistrationResponses")
    void testGetRegistrationsByUserReturnsEnrichedList() {
        when(registrationService.getRegistrationsByUser(10L)).thenReturn(List.of(sampleRegistration));

        ResponseEntity<List<RegistrationResponse>> responseEntity = registrationController.getRegistrationsByUser(10L);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        List<RegistrationResponse> list = responseEntity.getBody();
        assertNotNull(list);
        assertEquals(1, list.size());

        RegistrationResponse regResp = list.get(0);
        assertEquals(LocalDate.of(2026, 11, 15), regResp.getEventDate());
        assertEquals(LocalTime.of(9, 0), regResp.getEventStartTime());
        assertEquals("Innovation Lab", regResp.getEventVenue());
        assertEquals(EventStatus.PUBLISHED, regResp.getEventStatus());
    }

    @Test
    @DisplayName("toResponse handles registration with null event or null user gracefully")
    void testNullEventAndUserMapping() {
        Registration emptyReg = new Registration();
        emptyReg.setId(200L);
        emptyReg.setType(RegistrationType.VOLUNTEER);
        emptyReg.setStatus(RegistrationStatus.REGISTERED);
        emptyReg.setRegisteredAt(LocalDateTime.now());

        when(registrationService.getRegistrationById(200L)).thenReturn(emptyReg);

        ResponseEntity<RegistrationResponse> responseEntity = registrationController.getRegistrationById(200L);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        RegistrationResponse body = responseEntity.getBody();
        assertNotNull(body);

        assertEquals(200L, body.getId());
        assertNull(body.getUserId());
        assertNull(body.getUserName());
        assertNull(body.getEventId());
        assertNull(body.getEventTitle());
        assertNull(body.getEventDate());
        assertNull(body.getEventStartTime());
        assertNull(body.getEventVenue());
        assertNull(body.getEventStatus());
    }

    @Test
    @DisplayName("cancelRegistration returns enriched RegistrationResponse with updated status")
    void testCancelRegistrationReturnsEnrichedResponse() {
        sampleRegistration.setStatus(RegistrationStatus.CANCELLED);
        when(registrationService.cancelRegistration(100L)).thenReturn(sampleRegistration);

        ResponseEntity<RegistrationResponse> responseEntity = registrationController.cancelRegistration(100L);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        RegistrationResponse body = responseEntity.getBody();
        assertNotNull(body);
        assertEquals(RegistrationStatus.CANCELLED, body.getStatus());
        assertEquals(LocalDate.of(2026, 11, 15), body.getEventDate());
        assertEquals("Innovation Lab", body.getEventVenue());
    }

    @Test
    @DisplayName("getRegistrationSummaryByEvent returns summary from service")
    void testGetRegistrationSummaryByEvent() {
        com.eventease.dto.response.EventRegistrationSummaryResponse mockSummary =
                new com.eventease.dto.response.EventRegistrationSummaryResponse(
                        20L, 10L, 8L, 2L, 7L, 2L, 1L, 0L, java.util.Map.of("REGISTERED", 7L)
                );
        when(registrationService.getRegistrationSummaryForEvent(20L)).thenReturn(mockSummary);

        ResponseEntity<com.eventease.dto.response.EventRegistrationSummaryResponse> response =
                registrationController.getRegistrationSummaryByEvent(20L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(20L, response.getBody().getEventId());
        assertEquals(10L, response.getBody().getTotalRegistrations());
        assertEquals(8L, response.getBody().getParticipantCount());
        assertEquals(2L, response.getBody().getVolunteerCount());
        verify(registrationService).getRegistrationSummaryForEvent(20L);
    }

    @Test
    @DisplayName("getRegistrationsByEvent forwards type and status filter parameters")
    void testGetRegistrationsByEventWithFilters() {
        when(registrationService.getRegistrationsByEvent(20L, RegistrationType.PARTICIPANT, RegistrationStatus.REGISTERED))
                .thenReturn(List.of(sampleRegistration));

        ResponseEntity<List<RegistrationResponse>> response =
                registrationController.getRegistrationsByEvent(20L, RegistrationType.PARTICIPANT, RegistrationStatus.REGISTERED);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("Jane Doe", response.getBody().get(0).getUserName());
        assertEquals("jane@example.com", response.getBody().get(0).getUserEmail());
        verify(registrationService).getRegistrationsByEvent(20L, RegistrationType.PARTICIPANT, RegistrationStatus.REGISTERED);
    }
}
