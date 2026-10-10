package com.eventease.dto.request;

import com.eventease.entity.RegistrationType;
import jakarta.validation.constraints.NotNull;

public class RegistrationRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Event ID is required")
    private Long eventId;

    @NotNull(message = "Registration type is required")
    private RegistrationType type;

    public RegistrationRequest() {
    }

    public RegistrationRequest(Long userId, Long eventId, RegistrationType type) {
        this.userId = userId;
        this.eventId = eventId;
        this.type = type;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public RegistrationType getType() {
        return type;
    }

    public void setType(RegistrationType type) {
        this.type = type;
    }
}
