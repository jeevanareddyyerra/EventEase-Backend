package com.eventease.dto.response;

import java.time.LocalDateTime;

public class AttendanceResponse {

    private Long id;
    private Long registrationId;
    private Boolean attended;
    private LocalDateTime markedAt;

    public AttendanceResponse() {
    }

    public AttendanceResponse(Long id, Long registrationId, Boolean attended, LocalDateTime markedAt) {
        this.id = id;
        this.registrationId = registrationId;
        this.attended = attended;
        this.markedAt = markedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getMarkedAt() {
        return markedAt;
    }

    public void setMarkedAt(LocalDateTime markedAt) {
        this.markedAt = markedAt;
    }
}
