package com.eventease.controller;

import com.eventease.dto.request.RegistrationRequest;
import com.eventease.dto.request.VolunteerStatusUpdateRequest;
import com.eventease.dto.response.EventRegistrationSummaryResponse;
import com.eventease.dto.response.RegistrationResponse;
import com.eventease.entity.Event;
import com.eventease.entity.EventStatus;
import com.eventease.entity.Registration;
import com.eventease.entity.RegistrationStatus;
import com.eventease.entity.RegistrationType;
import com.eventease.entity.User;
import com.eventease.service.EventService;
import com.eventease.service.RegistrationService;
import com.eventease.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {

    private final RegistrationService registrationService;
    private final UserService userService;
    private final EventService eventService;

    public RegistrationController(RegistrationService registrationService,
                                  UserService userService,
                                  EventService eventService) {
        this.registrationService = registrationService;
        this.userService = userService;
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<RegistrationResponse> createRegistration(@Valid @RequestBody RegistrationRequest request) {
        Registration created = registrationService.createRegistration(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RegistrationResponse> getRegistrationById(@PathVariable Long id) {
        Registration registration = registrationService.getRegistrationById(id);
        return ResponseEntity.ok(toResponse(registration));
    }

    @GetMapping
    public ResponseEntity<List<RegistrationResponse>> getAllRegistrations() {
        List<RegistrationResponse> responses = registrationService.getAllRegistrations()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<List<RegistrationResponse>> getRegistrationsByEvent(
            @PathVariable Long eventId,
            @RequestParam(required = false) RegistrationType type,
            @RequestParam(required = false) RegistrationStatus status
    ) {
        List<RegistrationResponse> responses = registrationService.getRegistrationsByEvent(eventId, type, status)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/event/{eventId}/summary")
    public ResponseEntity<EventRegistrationSummaryResponse> getRegistrationSummaryByEvent(@PathVariable Long eventId) {
        EventRegistrationSummaryResponse summary = registrationService.getRegistrationSummaryForEvent(eventId);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<RegistrationResponse>> getRegistrationsByUser(@PathVariable Long userId) {
        List<RegistrationResponse> responses = registrationService.getRegistrationsByUser(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/event/{eventId}/participant-count")
    public ResponseEntity<Long> getParticipantCount(@PathVariable Long eventId) {
        long count = registrationService.getParticipantCount(eventId);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/event/{eventId}/volunteer-count")
    public ResponseEntity<Long> getVolunteerCount(@PathVariable Long eventId) {
        long count = registrationService.getVolunteerCount(eventId);
        return ResponseEntity.ok(count);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RegistrationResponse> updateRegistration(@PathVariable Long id,
                                                                   @Valid @RequestBody RegistrationRequest request) {
        Registration registration = toEntity(request);
        registration.setStatus(null);
        Registration updated = registrationService.updateRegistration(id, registration);
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRegistration(@PathVariable Long id) {
        registrationService.deleteRegistration(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<RegistrationResponse> cancelRegistration(@PathVariable Long id) {
        Registration cancelled = registrationService.cancelRegistration(id);
        return ResponseEntity.ok(toResponse(cancelled));
    }

    @PatchMapping("/{id}/volunteer-status")
    public ResponseEntity<RegistrationResponse> updateVolunteerStatus(
            @PathVariable Long id,
            @Valid @RequestBody VolunteerStatusUpdateRequest request) {
        Registration updated = registrationService.updateVolunteerStatus(id, request.getStatus());
        return ResponseEntity.ok(toResponse(updated));
    }

    private Registration toEntity(RegistrationRequest request) {
        User user = userService.getUserById(request.getUserId());
        Event event = eventService.getEventById(request.getEventId());
        Registration registration = new Registration();
        registration.setUser(user);
        registration.setEvent(event);
        registration.setType(request.getType());
        registration.setStatus(RegistrationStatus.REGISTERED);
        return registration;
    }

    private RegistrationResponse toResponse(Registration registration) {
        Long userId = registration.getUser() != null ? registration.getUser().getId() : null;
        String userName = registration.getUser() != null ? registration.getUser().getName() : null;
        String userEmail = registration.getUser() != null ? registration.getUser().getEmail() : null;
        Long eventId = registration.getEvent() != null ? registration.getEvent().getId() : null;
        String eventTitle = registration.getEvent() != null ? registration.getEvent().getTitle() : null;
        LocalDate eventDate = registration.getEvent() != null ? registration.getEvent().getDate() : null;
        LocalTime eventStartTime = registration.getEvent() != null ? registration.getEvent().getStartTime() : null;
        String eventVenue = registration.getEvent() != null ? registration.getEvent().getVenue() : null;
        EventStatus eventStatus = registration.getEvent() != null ? registration.getEvent().getStatus() : null;

        return new RegistrationResponse(
                registration.getId(),
                userId,
                userName,
                userEmail,
                eventId,
                eventTitle,
                eventDate,
                eventStartTime,
                eventVenue,
                eventStatus,
                registration.getType(),
                registration.getStatus(),
                registration.getRegisteredAt()
        );
    }
}
