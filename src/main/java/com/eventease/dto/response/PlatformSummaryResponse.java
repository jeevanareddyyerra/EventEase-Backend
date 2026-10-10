package com.eventease.dto.response;

public class PlatformSummaryResponse {

    private long totalUsers;
    private long totalOrganizers;
    private long totalParticipants;
    private long totalAdmins;
    private long totalEvents;
    private long publishedEvents;
    private long pendingEvents;
    private long approvedEvents;
    private long completedEvents;
    private long cancelledEvents;
    private long totalRegistrations;

    public PlatformSummaryResponse() {
    }

    public PlatformSummaryResponse(
            long totalUsers,
            long totalOrganizers,
            long totalParticipants,
            long totalAdmins,
            long totalEvents,
            long publishedEvents,
            long pendingEvents,
            long approvedEvents,
            long completedEvents,
            long cancelledEvents,
            long totalRegistrations
    ) {
        this.totalUsers = totalUsers;
        this.totalOrganizers = totalOrganizers;
        this.totalParticipants = totalParticipants;
        this.totalAdmins = totalAdmins;
        this.totalEvents = totalEvents;
        this.publishedEvents = publishedEvents;
        this.pendingEvents = pendingEvents;
        this.approvedEvents = approvedEvents;
        this.completedEvents = completedEvents;
        this.cancelledEvents = cancelledEvents;
        this.totalRegistrations = totalRegistrations;
    }

    public long getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(long totalUsers) {
        this.totalUsers = totalUsers;
    }

    public long getTotalOrganizers() {
        return totalOrganizers;
    }

    public void setTotalOrganizers(long totalOrganizers) {
        this.totalOrganizers = totalOrganizers;
    }

    public long getTotalParticipants() {
        return totalParticipants;
    }

    public void setTotalParticipants(long totalParticipants) {
        this.totalParticipants = totalParticipants;
    }

    public long getTotalAdmins() {
        return totalAdmins;
    }

    public void setTotalAdmins(long totalAdmins) {
        this.totalAdmins = totalAdmins;
    }

    public long getTotalEvents() {
        return totalEvents;
    }

    public void setTotalEvents(long totalEvents) {
        this.totalEvents = totalEvents;
    }

    public long getPublishedEvents() {
        return publishedEvents;
    }

    public void setPublishedEvents(long publishedEvents) {
        this.publishedEvents = publishedEvents;
    }

    public long getPendingEvents() {
        return pendingEvents;
    }

    public void setPendingEvents(long pendingEvents) {
        this.pendingEvents = pendingEvents;
    }

    public long getApprovedEvents() {
        return approvedEvents;
    }

    public void setApprovedEvents(long approvedEvents) {
        this.approvedEvents = approvedEvents;
    }

    public long getCompletedEvents() {
        return completedEvents;
    }

    public void setCompletedEvents(long completedEvents) {
        this.completedEvents = completedEvents;
    }

    public long getCancelledEvents() {
        return cancelledEvents;
    }

    public void setCancelledEvents(long cancelledEvents) {
        this.cancelledEvents = cancelledEvents;
    }

    public long getTotalRegistrations() {
        return totalRegistrations;
    }

    public void setTotalRegistrations(long totalRegistrations) {
        this.totalRegistrations = totalRegistrations;
    }
}
