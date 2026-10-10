package com.eventease.controller;

import com.eventease.dto.request.VolunteerRequirementRequest;
import com.eventease.dto.response.VolunteerRequirementResponse;
import com.eventease.entity.Event;
import com.eventease.entity.VolunteerRequirement;
import com.eventease.service.EventService;
import com.eventease.service.VolunteerRequirementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/volunteer-requirements")
public class VolunteerRequirementController {

    private final VolunteerRequirementService volunteerRequirementService;
    private final EventService eventService;

    public VolunteerRequirementController(VolunteerRequirementService volunteerRequirementService,
                                          EventService eventService) {
        this.volunteerRequirementService = volunteerRequirementService;
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<VolunteerRequirementResponse> createRequirement(
            @Valid @RequestBody VolunteerRequirementRequest request) {
        VolunteerRequirement requirement = toEntity(request);
        VolunteerRequirement created = volunteerRequirementService.createRequirement(requirement);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VolunteerRequirementResponse> getRequirementById(@PathVariable Long id) {
        VolunteerRequirement requirement = volunteerRequirementService.getRequirementById(id);
        return ResponseEntity.ok(toResponse(requirement));
    }

    @GetMapping
    public ResponseEntity<List<VolunteerRequirementResponse>> getAllRequirements() {
        List<VolunteerRequirementResponse> responses = volunteerRequirementService.getAllRequirements()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<VolunteerRequirementResponse> updateRequirement(
            @PathVariable Long id,
            @Valid @RequestBody VolunteerRequirementRequest request) {
        VolunteerRequirement requirement = toEntity(request);
        VolunteerRequirement updated = volunteerRequirementService.updateRequirement(id, requirement);
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRequirement(@PathVariable Long id) {
        volunteerRequirementService.deleteRequirement(id);
        return ResponseEntity.noContent().build();
    }

    private VolunteerRequirement toEntity(VolunteerRequirementRequest request) {
        Event event = eventService.getEventById(request.getEventId());
        VolunteerRequirement requirement = new VolunteerRequirement();
        requirement.setEvent(event);
        requirement.setRequiredCount(request.getRequiredCount());
        requirement.setDescription(request.getDescription());
        return requirement;
    }

    private VolunteerRequirementResponse toResponse(VolunteerRequirement requirement) {
        Long eventId = requirement.getEvent() != null ? requirement.getEvent().getId() : null;
        return new VolunteerRequirementResponse(
                requirement.getId(),
                eventId,
                requirement.getRequiredCount(),
                requirement.getDescription()
        );
    }
}
