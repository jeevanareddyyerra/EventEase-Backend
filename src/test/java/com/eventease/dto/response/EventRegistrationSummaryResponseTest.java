package com.eventease.dto.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventRegistrationSummaryResponseTest {

    @Test
    @DisplayName("Should construct with all arguments and map counts correctly")
    void testAllArgsConstructor() {
        Map<String, Long> statusCounts = Map.of(
                "REGISTERED", 12L,
                "ACCEPTED", 5L,
                "CANCELLED", 2L,
                "REJECTED", 1L
        );

        EventRegistrationSummaryResponse response = new EventRegistrationSummaryResponse(
                10L,
                20L,
                15L,
                5L,
                12L,
                5L,
                2L,
                1L,
                statusCounts
        );

        assertEquals(10L, response.getEventId());
        assertEquals(20L, response.getTotalRegistrations());
        assertEquals(15L, response.getParticipantCount());
        assertEquals(5L, response.getVolunteerCount());
        assertEquals(12L, response.getRegisteredCount());
        assertEquals(5L, response.getAcceptedCount());
        assertEquals(2L, response.getCancelledCount());
        assertEquals(1L, response.getRejectedCount());
        assertEquals(statusCounts, response.getStatusCounts());
    }

    @Test
    @DisplayName("Should handle empty and default constructor gracefully")
    void testDefaultConstructorAndSetters() {
        EventRegistrationSummaryResponse response = new EventRegistrationSummaryResponse();
        assertNotNull(response.getStatusCounts());
        assertTrue(response.getStatusCounts().isEmpty());

        response.setEventId(55L);
        response.setTotalRegistrations(0L);
        response.setParticipantCount(0L);
        response.setVolunteerCount(0L);
        response.setRegisteredCount(0L);
        response.setAcceptedCount(0L);
        response.setCancelledCount(0L);
        response.setRejectedCount(0L);
        response.setStatusCounts(null);

        assertEquals(55L, response.getEventId());
        assertEquals(0L, response.getTotalRegistrations());
        assertNotNull(response.getStatusCounts());
        assertTrue(response.getStatusCounts().isEmpty());
    }
}
