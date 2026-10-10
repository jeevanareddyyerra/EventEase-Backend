package com.eventease.dto.response;

import com.eventease.entity.EventStatus;
import com.eventease.entity.RegistrationStatus;
import com.eventease.entity.RegistrationType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class RegistrationResponse {

    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private Long eventId;
    private String eventTitle;
    private LocalDate eventDate;
    private LocalTime eventStartTime;
    private String eventVenue;
    private EventStatus eventStatus;
    private RegistrationType type;
    private RegistrationStatus status;
    private LocalDateTime registeredAt;

    public RegistrationResponse() {
    }

    /**
     * Backward-compatible 8-parameter constructor.
     */
    public RegistrationResponse(Long id, Long userId, String userName, Long eventId, String eventTitle,
                                RegistrationType type, RegistrationStatus status, LocalDateTime registeredAt) {
        this(id, userId, userName, null, eventId, eventTitle, null, null, null, null, type, status, registeredAt);
    }

    /**
     * Enriched 12-parameter constructor including event metadata.
     */
    public RegistrationResponse(Long id, Long userId, String userName, Long eventId, String eventTitle,
                                LocalDate eventDate, LocalTime eventStartTime, String eventVenue, EventStatus eventStatus,
                                RegistrationType type, RegistrationStatus status, LocalDateTime registeredAt) {
        this(id, userId, userName, null, eventId, eventTitle, eventDate, eventStartTime, eventVenue, eventStatus, type, status, registeredAt);
    }

    /**
     * Complete 13-parameter constructor including user email.
     */
    public RegistrationResponse(Long id, Long userId, String userName, String userEmail, Long eventId, String eventTitle,
                                LocalDate eventDate, LocalTime eventStartTime, String eventVenue, EventStatus eventStatus,
                                RegistrationType type, RegistrationStatus status, LocalDateTime registeredAt) {
        this.id = id;
        this.userId = userId;
        this.userName = userName;
        this.userEmail = userEmail;
        this.eventId = eventId;
        this.eventTitle = eventTitle;
        this.eventDate = eventDate;
        this.eventStartTime = eventStartTime;
        this.eventVenue = eventVenue;
        this.eventStatus = eventStatus;
        this.type = type;
        this.status = status;
        this.registeredAt = registeredAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public void setEventTitle(String eventTitle) {
        this.eventTitle = eventTitle;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public LocalTime getEventStartTime() {
        return eventStartTime;
    }

    public void setEventStartTime(LocalTime eventStartTime) {
        this.eventStartTime = eventStartTime;
    }

    public String getEventVenue() {
        return eventVenue;
    }

    public void setEventVenue(String eventVenue) {
        this.eventVenue = eventVenue;
    }

    public EventStatus getEventStatus() {
        return eventStatus;
    }

    public void setEventStatus(EventStatus eventStatus) {
        this.eventStatus = eventStatus;
    }

    public RegistrationType getType() {
        return type;
    }

    public void setType(RegistrationType type) {
        this.type = type;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public void setStatus(RegistrationStatus status) {
        this.status = status;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }
}
