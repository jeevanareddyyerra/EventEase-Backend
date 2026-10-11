package com.eventease.dto.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class EventRequestDeserializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Should deserialize EventRequest with HH:mm times and yyyy-MM-dd'T'HH:mm deadline")
    void testDeserializationWithShortTimeAndDeadline() throws Exception {
        String json = """
                {
                    "title": "Short Format Summit",
                    "date": "2026-11-20",
                    "startTime": "09:30",
                    "endTime": "17:45",
                    "registrationDeadline": "2026-11-19T23:59"
                }
                """;

        EventRequest request = objectMapper.readValue(json, EventRequest.class);

        assertNotNull(request);
        assertEquals("Short Format Summit", request.getTitle());
        assertEquals(LocalDate.of(2026, 11, 20), request.getDate());
        assertEquals(LocalTime.of(9, 30), request.getStartTime());
        assertEquals(LocalTime.of(17, 45), request.getEndTime());
        assertEquals(LocalDateTime.of(2026, 11, 19, 23, 59), request.getRegistrationDeadline());
    }

    @Test
    @DisplayName("Should deserialize EventRequest with HH:mm:ss times and yyyy-MM-dd'T'HH:mm:ss deadline")
    void testDeserializationWithSecondsTimeAndDeadline() throws Exception {
        String json = """
                {
                    "title": "Full Format Conference",
                    "date": "2026-11-20",
                    "startTime": "09:30:00",
                    "endTime": "17:45:00",
                    "registrationDeadline": "2026-11-19T23:59:00"
                }
                """;

        EventRequest request = objectMapper.readValue(json, EventRequest.class);

        assertNotNull(request);
        assertEquals("Full Format Conference", request.getTitle());
        assertEquals(LocalDate.of(2026, 11, 20), request.getDate());
        assertEquals(LocalTime.of(9, 30, 0), request.getStartTime());
        assertEquals(LocalTime.of(17, 45, 0), request.getEndTime());
        assertEquals(LocalDateTime.of(2026, 11, 19, 23, 59, 0), request.getRegistrationDeadline());
    }

    @Test
    @DisplayName("Should deserialize EventRequest with null optional dates and times")
    void testDeserializationWithNullOptionalFields() throws Exception {
        String json = """
                {
                    "title": "Minimal Event",
                    "date": "2026-11-20"
                }
                """;

        EventRequest request = objectMapper.readValue(json, EventRequest.class);

        assertNotNull(request);
        assertEquals("Minimal Event", request.getTitle());
        assertEquals(LocalDate.of(2026, 11, 20), request.getDate());
        assertNull(request.getStartTime());
        assertNull(request.getEndTime());
        assertNull(request.getRegistrationDeadline());
    }
}
