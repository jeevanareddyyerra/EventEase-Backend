package com.eventease.dto.request;

import com.eventease.entity.RegistrationStatus;
import jakarta.validation.constraints.NotNull;

public class VolunteerStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private RegistrationStatus status;

    public VolunteerStatusUpdateRequest() {
    }

    public VolunteerStatusUpdateRequest(RegistrationStatus status) {
        this.status = status;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public void setStatus(RegistrationStatus status) {
        this.status = status;
    }
}
