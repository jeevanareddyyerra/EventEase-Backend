package com.eventease.service;

import com.eventease.entity.Event;
import com.eventease.entity.EventStatus;
import com.eventease.exception.BusinessRuleException;
import com.eventease.exception.ResourceNotFoundException;
import com.eventease.repository.EventRepository;
import com.eventease.repository.EventSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    public static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "date", "title", "category", "createdAt", "capacity", "status", "venue"
    );
    public static final int MAX_PAGE_SIZE = 50;

    private static final Map<EventStatus, Set<EventStatus>> ALLOWED_TRANSITIONS = Map.of(
            EventStatus.PENDING, Set.of(EventStatus.APPROVED, EventStatus.CANCELLED),
            EventStatus.APPROVED, Set.of(EventStatus.PUBLISHED, EventStatus.CANCELLED, EventStatus.PENDING),
            EventStatus.PUBLISHED, Set.of(EventStatus.COMPLETED, EventStatus.CANCELLED),
            EventStatus.COMPLETED, Collections.emptySet(),
            EventStatus.CANCELLED, Collections.emptySet()
    );

    private final EventRepository eventRepository;
    private com.eventease.rag.index.DocumentIndexService documentIndexService;
    private com.eventease.repository.EventDocumentRepository eventDocumentRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDocumentIndexService(com.eventease.rag.index.DocumentIndexService documentIndexService) {
        this.documentIndexService = documentIndexService;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setEventDocumentRepository(com.eventease.repository.EventDocumentRepository eventDocumentRepository) {
        this.eventDocumentRepository = eventDocumentRepository;
    }

    @Transactional
    public Event createEvent(Event event) {
        // Enforce that new events always default to PENDING status
        event.setStatus(EventStatus.PENDING);
        if (event.getCreatedAt() == null) {
            event.setCreatedAt(LocalDateTime.now());
        }
        Event saved = eventRepository.save(event);
        log.info("Event created successfully [eventId={}, title='{}', status={}]", saved.getId(), saved.getTitle(), saved.getStatus());
        return saved;
    }

    public Event getEventById(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + id));
    }

    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    public List<Event> searchEvents(String keyword, String category, EventStatus status, Boolean upcoming) {
        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        boolean hasCategory = category != null && !category.trim().isEmpty() && !category.trim().equalsIgnoreCase("ALL");
        boolean hasStatus = status != null;
        boolean isUpcoming = Boolean.TRUE.equals(upcoming);

        if (!hasKeyword && !hasCategory && !hasStatus && !isUpcoming) {
            return getAllEvents();
        }

        Specification<Event> spec = EventSpecification.filterEvents(
                hasKeyword ? keyword.trim() : null,
                hasCategory ? category.trim() : null,
                status,
                isUpcoming
        );
        return eventRepository.findAll(spec);
    }

    public List<Event> searchEvents(String keyword, String category, String status, Boolean upcoming) {
        EventStatus eventStatus = null;
        if (status != null && !status.trim().isEmpty() && !status.trim().equalsIgnoreCase("ALL")) {
            try {
                eventStatus = EventStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleException("Invalid event status: " + status, HttpStatus.BAD_REQUEST);
            }
        }
        return searchEvents(keyword, category, eventStatus, upcoming);
    }

    public Page<Event> searchEvents(String keyword, String category, String status, Boolean upcoming,
                                    int page, int size, String sortBy, String direction) {
        if (page < 0) {
            throw new BusinessRuleException("Page index must not be less than zero", HttpStatus.BAD_REQUEST);
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new BusinessRuleException("Page size must be between 1 and " + MAX_PAGE_SIZE, HttpStatus.BAD_REQUEST);
        }

        String normalizedSortBy = (sortBy == null || sortBy.trim().isEmpty()) ? "date" : sortBy.trim();
        if (!ALLOWED_SORT_FIELDS.contains(normalizedSortBy)) {
            throw new BusinessRuleException(
                    "Invalid sort field: " + sortBy + ". Allowed fields are: " + String.join(", ", ALLOWED_SORT_FIELDS),
                    HttpStatus.BAD_REQUEST
            );
        }

        Sort.Direction sortDirection;
        if (direction == null || direction.trim().isEmpty() || direction.trim().equalsIgnoreCase("ASC")) {
            sortDirection = Sort.Direction.ASC;
        } else if (direction.trim().equalsIgnoreCase("DESC")) {
            sortDirection = Sort.Direction.DESC;
        } else {
            throw new BusinessRuleException(
                    "Invalid sort direction: " + direction + ". Allowed values are: asc, desc",
                    HttpStatus.BAD_REQUEST
            );
        }

        EventStatus eventStatus = null;
        if (status != null && !status.trim().isEmpty() && !status.trim().equalsIgnoreCase("ALL")) {
            try {
                eventStatus = EventStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleException("Invalid event status: " + status, HttpStatus.BAD_REQUEST);
            }
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, normalizedSortBy));

        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        boolean hasCategory = category != null && !category.trim().isEmpty() && !category.trim().equalsIgnoreCase("ALL");
        boolean hasStatus = eventStatus != null;
        boolean isUpcoming = Boolean.TRUE.equals(upcoming);

        if (!hasKeyword && !hasCategory && !hasStatus && !isUpcoming) {
            return eventRepository.findAll(pageable);
        }

        Specification<Event> spec = EventSpecification.filterEvents(
                hasKeyword ? keyword.trim() : null,
                hasCategory ? category.trim() : null,
                eventStatus,
                isUpcoming
        );
        return eventRepository.findAll(spec, pageable);
    }

    public List<Event> getEventsByStatus(EventStatus status) {
        return eventRepository.findByStatus(status);
    }

    public List<Event> getEventsByOrganizer(Long organizerId) {
        return eventRepository.findByOrganizerId(organizerId);
    }

    @Transactional
    public Event updateEvent(Long id, Event event) {
        Event existingEvent = getEventById(id);
        checkEventOwnership(existingEvent, "edit");
        existingEvent.setTitle(event.getTitle());
        existingEvent.setDescription(event.getDescription());
        existingEvent.setCategory(event.getCategory());
        existingEvent.setDate(event.getDate());
        existingEvent.setStartTime(event.getStartTime());
        existingEvent.setEndTime(event.getEndTime());
        existingEvent.setVenue(event.getVenue());
        existingEvent.setCapacity(event.getCapacity());
        existingEvent.setEligibility(event.getEligibility());
        if (event.getImageUrl() != null) {
            existingEvent.setImageUrl(event.getImageUrl());
        }
        // Status changes are intentionally prohibited through general update
        if (event.getOrganizer() != null) {
            existingEvent.setOrganizer(event.getOrganizer());
        }
        Event saved = eventRepository.save(existingEvent);
        if (documentIndexService != null) {
            documentIndexService.updateEventMetadata(id, saved.getTitle(), saved.getStatus());
        }
        return saved;
    }

    @Transactional
    public Event updateEventStatus(Long id, EventStatus newStatus) {
        if (newStatus == null) {
            throw new BusinessRuleException("Target status is required.", HttpStatus.BAD_REQUEST);
        }

        Event event = getEventById(id);
        EventStatus currentStatus = event.getStatus();

        Set<EventStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Collections.emptySet());
        if (!allowed.contains(newStatus)) {
            throw new BusinessRuleException(
                    String.format("Invalid event status transition from %s to %s", currentStatus, newStatus),
                    HttpStatus.BAD_REQUEST
            );
        }

        // Role & ownership check
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean isOrganizer = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZER"));
            boolean isUserOnly = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")) && !isAdmin && !isOrganizer;

            if (isUserOnly) {
                throw new BusinessRuleException("Users are not authorized to change event status.", HttpStatus.FORBIDDEN);
            }

            if (isOrganizer && !isAdmin) {
                if (event.getOrganizer() != null && event.getOrganizer().getEmail() != null
                        && !event.getOrganizer().getEmail().equalsIgnoreCase(auth.getName())) {
                    throw new BusinessRuleException("Organizers can only change status for their own events.", HttpStatus.FORBIDDEN);
                }
            }
        }

        event.setStatus(newStatus);
        Event saved = eventRepository.save(event);
        log.info("Event status updated [eventId={}, from={}, to={}]", id, currentStatus, newStatus);

        if (documentIndexService != null) {
            // Update status on all existing chunks in the index
            documentIndexService.updateEventStatus(id, newStatus);

            if (newStatus == EventStatus.CANCELLED) {
                documentIndexService.removeEventDocuments(id);
            } else if (newStatus == EventStatus.PUBLISHED) {
                List<com.eventease.entity.EventDocument> docs = null;
                if (eventDocumentRepository != null) {
                    docs = eventDocumentRepository.findByEventId(id);
                } else if (saved.getDocuments() != null) {
                    docs = saved.getDocuments();
                }
                if (docs != null) {
                    for (com.eventease.entity.EventDocument doc : docs) {
                        documentIndexService.indexDocument(doc);
                    }
                }
            }
        }

        return saved;
    }

    @Transactional
    public void deleteEvent(Long id) {
        if (!eventRepository.existsById(id)) {
            log.warn("Event deletion failed - event not found with id: {}", id);
            throw new ResourceNotFoundException("Event not found with id: " + id);
        }
        eventRepository.findById(id).ifPresent(event -> checkEventOwnership(event, "delete"));
        if (documentIndexService != null) {
            documentIndexService.removeEventDocuments(id);
        }
        eventRepository.deleteById(id);
        log.info("Event deleted successfully [eventId={}]", id);
    }

    private void checkEventOwnership(Event event, String action) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean isOrganizer = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZER"));
            boolean isUserOnly = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")) && !isAdmin && !isOrganizer;

            if (isUserOnly) {
                throw new BusinessRuleException("Users are not authorized to " + action + " events.", HttpStatus.FORBIDDEN);
            }

            if (isOrganizer && !isAdmin) {
                if (event.getOrganizer() != null && event.getOrganizer().getEmail() != null
                        && !event.getOrganizer().getEmail().equalsIgnoreCase(auth.getName())) {
                    throw new BusinessRuleException("Organizers can only " + action + " their own events.", HttpStatus.FORBIDDEN);
                }
            }
        }
    }
}
