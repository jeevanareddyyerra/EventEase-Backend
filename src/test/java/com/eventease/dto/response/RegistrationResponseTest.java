package com.eventease.dto.response;

import com.eventease.entity.EventStatus;
import com.eventease.entity.RegistrationStatus;
import com.eventease.entity.RegistrationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationResponseTest {

    @Test
    @DisplayName("Should construct with full 13 arguments and map userEmail correctly")
    void test13ArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate date = LocalDate.of(2026, 12, 15);
        LocalTime time = LocalTime.of(10, 30);

        RegistrationResponse response = new RegistrationResponse(
                1L,
                2L,
                "Alice",
                "alice@example.com",
                3L,
                "Tech Summit",
                date,
                time,
                "Hall A",
                EventStatus.PUBLISHED,
                RegistrationType.PARTICIPANT,
                RegistrationStatus.REGISTERED,
                now
        );

        assertEquals(1L, response.getId());
        assertEquals(2L, response.getUserId());
        assertEquals("Alice", response.getUserName());
        assertEquals("alice@example.com", response.getUserEmail());
        assertEquals(3L, response.getEventId());
        assertEquals("Tech Summit", response.getEventTitle());
        assertEquals(date, response.getEventDate());
        assertEquals(time, response.getEventStartTime());
        assertEquals("Hall A", response.getEventVenue());
        assertEquals(EventStatus.PUBLISHED, response.getEventStatus());
        assertEquals(RegistrationType.PARTICIPANT, response.getType());
        assertEquals(RegistrationStatus.REGISTERED, response.getStatus());
        assertEquals(now, response.getRegisteredAt());
    }

    @Test
    @DisplayName("Should construct with full 12 arguments and map all enriched fields correctly")
    void testFullArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate date = LocalDate.of(2026, 12, 15);
        LocalTime time = LocalTime.of(10, 30);

        RegistrationResponse response = new RegistrationResponse(
                1L,
                2L,
                "Alice",
                3L,
                "Tech Summit",
                date,
                time,
                "Hall A",
                EventStatus.PUBLISHED,
                RegistrationType.PARTICIPANT,
                RegistrationStatus.REGISTERED,
                now
        );

        assertEquals(1L, response.getId());
        assertEquals(2L, response.getUserId());
        assertEquals("Alice", response.getUserName());
        assertEquals(3L, response.getEventId());
        assertEquals("Tech Summit", response.getEventTitle());
        assertEquals(date, response.getEventDate());
        assertEquals(time, response.getEventStartTime());
        assertEquals("Hall A", response.getEventVenue());
        assertEquals(EventStatus.PUBLISHED, response.getEventStatus());
        assertEquals(RegistrationType.PARTICIPANT, response.getType());
        assertEquals(RegistrationStatus.REGISTERED, response.getStatus());
        assertEquals(now, response.getRegisteredAt());
    }

    @Test
    @DisplayName("Should support backward-compatible 8-argument constructor with null event metadata")
    void testBackwardCompatibleConstructor() {
        LocalDateTime now = LocalDateTime.now();

        RegistrationResponse response = new RegistrationResponse(
                1L,
                2L,
                "Bob",
                3L,
                "Workshop",
                RegistrationType.VOLUNTEER,
                RegistrationStatus.ACCEPTED,
                now
        );

        assertEquals(1L, response.getId());
        assertEquals(2L, response.getUserId());
        assertEquals("Bob", response.getUserName());
        assertEquals(3L, response.getEventId());
        assertEquals("Workshop", response.getEventTitle());
        assertNull(response.getEventDate());
        assertNull(response.getEventStartTime());
        assertNull(response.getEventVenue());
        assertNull(response.getEventStatus());
        assertEquals(RegistrationType.VOLUNTEER, response.getType());
        assertEquals(RegistrationStatus.ACCEPTED, response.getStatus());
        assertEquals(now, response.getRegisteredAt());
    }

    @Test
    @DisplayName("Should support getters and setters for enriched fields")
    void testSettersAndGetters() {
        RegistrationResponse response = new RegistrationResponse();
        LocalDate date = LocalDate.of(2026, 11, 20);
        LocalTime time = LocalTime.of(14, 0);

        response.setEventDate(date);
        response.setEventStartTime(time);
        response.setEventVenue("Auditorium 2");
        response.setEventStatus(EventStatus.APPROVED);

        assertEquals(date, response.getEventDate());
        assertEquals(time, response.getEventStartTime());
        assertEquals("Auditorium 2", response.getEventVenue());
        assertEquals(EventStatus.APPROVED, response.getEventStatus());
    }
}
