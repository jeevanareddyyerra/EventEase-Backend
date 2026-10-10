package com.eventease.dto.response;

import java.util.Collections;
import java.util.Map;

public class EventRegistrationSummaryResponse {

    private Long eventId;
    private long totalRegistrations;
    private long participantCount;
    private long volunteerCount;
    private long registeredCount;
    private long acceptedCount;
    private long cancelledCount;
    private long rejectedCount;
    private Map<String, Long> statusCounts;

    public EventRegistrationSummaryResponse() {
        this.statusCounts = Collections.emptyMap();
    }

    public EventRegistrationSummaryResponse(
            Long eventId,
            long totalRegistrations,
            long participantCount,
            long volunteerCount,
            long registeredCount,
            long acceptedCount,
            long cancelledCount,
            long rejectedCount,
            Map<String, Long> statusCounts
    ) {
        this.eventId = eventId;
        this.totalRegistrations = totalRegistrations;
        this.participantCount = participantCount;
        this.volunteerCount = volunteerCount;
        this.registeredCount = registeredCount;
        this.acceptedCount = acceptedCount;
        this.cancelledCount = cancelledCount;
        this.rejectedCount = rejectedCount;
        this.statusCounts = statusCounts != null ? statusCounts : Collections.emptyMap();
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public long getTotalRegistrations() {
        return totalRegistrations;
    }

    public void setTotalRegistrations(long totalRegistrations) {
        this.totalRegistrations = totalRegistrations;
    }

    public long getParticipantCount() {
        return participantCount;
    }

    public void setParticipantCount(long participantCount) {
        this.participantCount = participantCount;
    }

    public long getVolunteerCount() {
        return volunteerCount;
    }

    public void setVolunteerCount(long volunteerCount) {
        this.volunteerCount = volunteerCount;
    }

    public long getRegisteredCount() {
        return registeredCount;
    }

    public void setRegisteredCount(long registeredCount) {
        this.registeredCount = registeredCount;
    }

    public long getAcceptedCount() {
        return acceptedCount;
    }

    public void setAcceptedCount(long acceptedCount) {
        this.acceptedCount = acceptedCount;
    }

    public long getCancelledCount() {
        return cancelledCount;
    }

    public void setCancelledCount(long cancelledCount) {
        this.cancelledCount = cancelledCount;
    }

    public long getRejectedCount() {
        return rejectedCount;
    }

    public void setRejectedCount(long rejectedCount) {
        this.rejectedCount = rejectedCount;
    }

    public Map<String, Long> getStatusCounts() {
        return statusCounts;
    }

    public void setStatusCounts(Map<String, Long> statusCounts) {
        this.statusCounts = statusCounts != null ? statusCounts : Collections.emptyMap();
    }
}
