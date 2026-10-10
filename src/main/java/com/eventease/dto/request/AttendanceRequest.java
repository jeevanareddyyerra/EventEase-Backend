package com.eventease.dto.request;

import jakarta.validation.constraints.NotNull;

public class AttendanceRequest {

    private Long registrationId;

    @NotNull(message = "Attended status is required")
    private Boolean attended;

    public AttendanceRequest() {
    }

    public AttendanceRequest(Boolean attended) {
        this.attended = attended;
    }

    public AttendanceRequest(Long registrationId, Boolean attended) {
        this.registrationId = registrationId;
        this.attended = attended;
    }

    public Long getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(Long registrationId) {
        this.registrationId = registrationId;
    }

    public Boolean getAttended() {
        return attended;
    }

    public void setAttended(Boolean attended) {
        this.attended = attended;
    }
}
