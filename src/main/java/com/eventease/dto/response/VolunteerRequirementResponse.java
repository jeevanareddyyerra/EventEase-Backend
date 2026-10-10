package com.eventease.dto.response;

public class VolunteerRequirementResponse {

    private Long id;
    private Long eventId;
    private Integer requiredCount;
    private String description;

    public VolunteerRequirementResponse() {
    }

    public VolunteerRequirementResponse(Long id, Long eventId, Integer requiredCount, String description) {
        this.id = id;
        this.eventId = eventId;
        this.requiredCount = requiredCount;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
