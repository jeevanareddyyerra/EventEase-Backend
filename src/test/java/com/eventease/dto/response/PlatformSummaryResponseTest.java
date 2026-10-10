package com.eventease.dto.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlatformSummaryResponseTest {

    @Test
    @DisplayName("Should construct with full arguments correctly")
    void shouldConstructWithFullArguments() {
        PlatformSummaryResponse response = new PlatformSummaryResponse(
                50L,
                10L,
                38L,
                2L,
                25L,
                15L,
                5L,
                3L,
                1L,
                1L,
                120L
        );

        assertEquals(50L, response.getTotalUsers());
        assertEquals(10L, response.getTotalOrganizers());
        assertEquals(38L, response.getTotalParticipants());
        assertEquals(2L, response.getTotalAdmins());
        assertEquals(25L, response.getTotalEvents());
        assertEquals(15L, response.getPublishedEvents());
        assertEquals(5L, response.getPendingEvents());
        assertEquals(3L, response.getApprovedEvents());
        assertEquals(1L, response.getCompletedEvents());
        assertEquals(1L, response.getCancelledEvents());
        assertEquals(120L, response.getTotalRegistrations());
    }

    @Test
    @DisplayName("Should allow mutation via setters")
    void shouldAllowMutationViaSetters() {
        PlatformSummaryResponse response = new PlatformSummaryResponse();
        response.setTotalUsers(100L);
        response.setTotalOrganizers(20L);
        response.setTotalParticipants(75L);
        response.setTotalAdmins(5L);
        response.setTotalEvents(60L);
        response.setPublishedEvents(40L);
        response.setPendingEvents(10L);
        response.setApprovedEvents(5L);
        response.setCompletedEvents(3L);
        response.setCancelledEvents(2L);
        response.setTotalRegistrations(300L);

        assertEquals(100L, response.getTotalUsers());
        assertEquals(20L, response.getTotalOrganizers());
        assertEquals(75L, response.getTotalParticipants());
        assertEquals(5L, response.getTotalAdmins());
        assertEquals(60L, response.getTotalEvents());
        assertEquals(40L, response.getPublishedEvents());
        assertEquals(10L, response.getPendingEvents());
        assertEquals(5L, response.getApprovedEvents());
        assertEquals(3L, response.getCompletedEvents());
        assertEquals(2L, response.getCancelledEvents());
        assertEquals(300L, response.getTotalRegistrations());
    }
}
