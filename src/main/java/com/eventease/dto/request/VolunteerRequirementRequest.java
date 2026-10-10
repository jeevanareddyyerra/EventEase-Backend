package com.eventease.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class VolunteerRequirementRequest {

    @NotNull(message = "Event ID is required")
    private Long eventId;

    @NotNull(message = "Required count is required")
    @Min(value = 1, message = "Required count must be at least 1")
    private Integer requiredCount;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    public VolunteerRequirementRequest() {
    }

    public VolunteerRequirementRequest(Long eventId, Integer requiredCount, String description) {
        this.eventId = eventId;
        this.requiredCount = requiredCount;
        this.description = description;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Integer getRequiredCount() {
        return requiredCount;
    }

    public void setRequiredCount(Integer requiredCount) {
        this.requiredCount = requiredCount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
