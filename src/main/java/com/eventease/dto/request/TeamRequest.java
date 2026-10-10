package com.eventease.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class TeamRequest {

    @NotBlank(message = "Team name is required")
    @Size(max = 255, message = "Team name must not exceed 255 characters")
    private String name;

    @NotNull(message = "Event ID is required")
    private Long eventId;

    private Long leaderId;

    public TeamRequest() {
    }

    public TeamRequest(String name, Long eventId) {
        this.name = name;
        this.eventId = eventId;
    }

    public TeamRequest(String name, Long eventId, Long leaderId) {
        this.name = name;
        this.eventId = eventId;
        this.leaderId = leaderId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Long getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(Long leaderId) {
        this.leaderId = leaderId;
    }
}
