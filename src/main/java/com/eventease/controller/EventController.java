package com.eventease.controller;

import com.eventease.dto.request.EventRequest;
import com.eventease.dto.request.EventStatusUpdateRequest;
import com.eventease.dto.response.EventResponse;
import com.eventease.dto.response.PagedResponse;
import com.eventease.entity.Event;
import com.eventease.entity.EventStatus;
import com.eventease.entity.User;
import com.eventease.service.EventService;
import com.eventease.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final UserService userService;

    public EventController(EventService eventService, UserService userService) {
        this.eventService = eventService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody EventRequest request) {
        Event event = toEntity(request);
        Event created = eventService.createEvent(event);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEventById(@PathVariable Long id) {
        Event event = eventService.getEventById(id);
        return ResponseEntity.ok(toResponse(event));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<EventResponse>> getAllEvents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean upcoming,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        Page<Event> eventsPage = eventService.searchEvents(
                keyword, category, status, upcoming, page, size, sortBy, direction
        );
        Page<EventResponse> responsePage = eventsPage.map(this::toResponse);
        return ResponseEntity.ok(PagedResponse.fromPage(responsePage));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<EventResponse>> getEventsByStatus(@PathVariable EventStatus status) {
        List<EventResponse> responses = eventService.getEventsByStatus(status)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/organizer/{organizerId}")
    public ResponseEntity<List<EventResponse>> getEventsByOrganizer(@PathVariable Long organizerId) {
        List<EventResponse> responses = eventService.getEventsByOrganizer(organizerId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> updateEvent(@PathVariable Long id, @Valid @RequestBody EventRequest request) {
        Event event = toEntity(request);
        event.setStatus(null);
        Event updated = eventService.updateEvent(id, event);
        return ResponseEntity.ok(toResponse(updated));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<EventResponse> updateEventStatus(
            @PathVariable Long id,
            @Valid @RequestBody EventStatusUpdateRequest request) {
        Event updated = eventService.updateEventStatus(id, request.getStatus());
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id) {
        eventService.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }

    private Event toEntity(EventRequest request) {
        User organizer = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean isOrganizer = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZER"));
            if (isOrganizer && !isAdmin) {
                organizer = userService.getUserByEmail(auth.getName());
            } else if (isAdmin && request.getOrganizerId() != null) {
                organizer = userService.getUserById(request.getOrganizerId());
            } else {
                organizer = userService.getUserByEmail(auth.getName());
            }
        }
        if (organizer == null && request.getOrganizerId() != null) {
            organizer = userService.getUserById(request.getOrganizerId());
        }
        if (organizer == null) {
            throw new com.eventease.exception.BusinessRuleException("Organizer ID is required or user must be authenticated", HttpStatus.BAD_REQUEST);
        }

        Event event = new Event();
        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setCategory(request.getCategory());
        event.setDate(request.getDate());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        event.setVenue(request.getVenue());
        event.setCapacity(request.getCapacity());
        event.setEligibility(request.getEligibility());
        event.setRegistrationDeadline(request.getRegistrationDeadline());
        event.setImageUrl(request.getImageUrl());
        event.setStatus(EventStatus.PENDING);
        event.setOrganizer(organizer);
        return event;
    }

    private EventResponse toResponse(Event event) {
        Long organizerId = event.getOrganizer() != null ? event.getOrganizer().getId() : null;
        String organizerName = event.getOrganizer() != null ? event.getOrganizer().getName() : null;
        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getCategory(),
                event.getDate(),
                event.getStartTime(),
                event.getEndTime(),
                event.getVenue(),
                event.getCapacity(),
                event.getEligibility(),
                event.getRegistrationDeadline(),
                event.getStatus(),
                organizerId,
                organizerName,
                event.getCreatedAt(),
                event.getImageUrl()
        );
    }
}
