package com.eventease.dto.request;

import com.eventease.entity.EventStatus;
import jakarta.validation.constraints.NotNull;

public class EventStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private EventStatus status;

    public EventStatusUpdateRequest() {
    }

    public EventStatusUpdateRequest(EventStatus status) {
        this.status = status;
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
    }
}
