package com.eventease.controller;

import com.eventease.dto.response.EventResponse;
import com.eventease.dto.response.PagedResponse;
import com.eventease.entity.Event;
import com.eventease.entity.EventStatus;
import com.eventease.service.EventService;
import com.eventease.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventControllerTest {

    @Mock
    private EventService eventService;

    @Mock
    private UserService userService;

    @InjectMocks
    private EventController eventController;

    private Event sampleEvent;

    @BeforeEach
    void setUp() {
        sampleEvent = new Event();
        sampleEvent.setId(1L);
        sampleEvent.setTitle("Tech Summit 2026");
        sampleEvent.setDescription("Annual developer conference");
        sampleEvent.setCategory("Technology");
        sampleEvent.setDate(LocalDate.of(2026, 11, 20));
        sampleEvent.setStartTime(LocalTime.of(10, 0));
        sampleEvent.setVenue("Grand Hall");
        sampleEvent.setCapacity(200);
        sampleEvent.setStatus(EventStatus.PUBLISHED);
        sampleEvent.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("getAllEvents returns PagedResponse with correct metadata and mapped content")
    void testGetAllEventsReturnsPagedResponse() {
        Page<Event> eventPage = new PageImpl<>(List.of(sampleEvent));
        when(eventService.searchEvents(isNull(), isNull(), isNull(), isNull(), eq(0), eq(10), eq("date"), eq("asc")))
                .thenReturn(eventPage);

        ResponseEntity<PagedResponse<EventResponse>> responseEntity = eventController.getAllEvents(
                null, null, null, null, 0, 10, "date", "asc"
        );

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        PagedResponse<EventResponse> body = responseEntity.getBody();
        assertNotNull(body);
        assertEquals(1, body.getContent().size());
        assertEquals("Tech Summit 2026", body.getContent().get(0).getTitle());
        assertEquals(0, body.getPage());
        assertEquals(1, body.getSize());
        assertEquals(1L, body.getTotalElements());
        assertEquals(1, body.getTotalPages());
    }

    @Test
    @DisplayName("getAllEvents forwards custom pagination and filtering parameters to service")
    void testGetAllEventsForwardsCustomParameters() {
        Page<Event> eventPage = new PageImpl<>(List.of(sampleEvent));
        when(eventService.searchEvents(eq("Tech"), eq("Technology"), eq("PUBLISHED"), eq(true), eq(2), eq(20), eq("title"), eq("desc")))
                .thenReturn(eventPage);

        ResponseEntity<PagedResponse<EventResponse>> responseEntity = eventController.getAllEvents(
                "Tech", "Technology", "PUBLISHED", true, 2, 20, "title", "desc"
        );

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        verify(eventService).searchEvents("Tech", "Technology", "PUBLISHED", true, 2, 20, "title", "desc");
    }
}
