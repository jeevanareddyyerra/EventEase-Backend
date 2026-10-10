package com.eventease.service;

import com.eventease.dto.request.RegistrationRequest;
import com.eventease.dto.response.EventRegistrationSummaryResponse;
import com.eventease.entity.*;
import com.eventease.exception.BusinessRuleException;
import com.eventease.exception.ResourceNotFoundException;
import com.eventease.repository.EventRepository;
import com.eventease.repository.RegistrationRepository;
import com.eventease.repository.UserRepository;
import com.eventease.repository.VolunteerRequirementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private static final List<RegistrationStatus> ACTIVE_CAPACITY_STATUSES = List.of(
            RegistrationStatus.REGISTERED,
            RegistrationStatus.ACCEPTED
    );

    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final VolunteerRequirementRepository volunteerRequirementRepository;

    public RegistrationService(
            RegistrationRepository registrationRepository,
            EventRepository eventRepository,
            UserRepository userRepository,
            VolunteerRequirementRepository volunteerRequirementRepository
    ) {
        this.registrationRepository = registrationRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.volunteerRequirementRepository = volunteerRequirementRepository;
    }

    @Transactional
    public Registration createRegistration(RegistrationRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Registration request cannot be null.", HttpStatus.BAD_REQUEST);
        }
        if (request.getEventId() == null) {
            throw new BusinessRuleException("Event ID is required.", HttpStatus.BAD_REQUEST);
        }
        if (request.getType() == null) {
            throw new BusinessRuleException("Registration type is required and must be PARTICIPANT or VOLUNTEER.", HttpStatus.BAD_REQUEST);
        }

        // 1. EVENT EXISTENCE
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + request.getEventId()));

        // 2. USER IDENTITY / SECURITY
        User user = resolveUser(request.getUserId());

        // 3. EVENT STATUS
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new BusinessRuleException(
                    "Cannot register for event with status: " + event.getStatus() + ". Only PUBLISHED events allow registration.",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 4. REGISTRATION DEADLINE
        if (event.getRegistrationDeadline() != null && LocalDateTime.now().isAfter(event.getRegistrationDeadline())) {
            throw new BusinessRuleException("Registration deadline has passed for this event.", HttpStatus.BAD_REQUEST);
        }

        // 5. DUPLICATE REGISTRATION
        if (registrationRepository.existsByUserIdAndEventId(user.getId(), event.getId())) {
            throw new BusinessRuleException("User is already registered for this event.", HttpStatus.CONFLICT);
        }

        // 6. PARTICIPANT CAPACITY
        if (request.getType() == RegistrationType.PARTICIPANT) {
            if (event.getCapacity() != null) {
                long currentParticipants = registrationRepository.countByEventIdAndTypeAndStatusIn(
                        event.getId(),
                        RegistrationType.PARTICIPANT,
                        ACTIVE_CAPACITY_STATUSES
                );
                if (currentParticipants >= event.getCapacity()) {
                    throw new BusinessRuleException("Participant capacity has been reached for this event.", HttpStatus.CONFLICT);
                }
            }
        }

        // 7. VOLUNTEER REGISTRATION
        else if (request.getType() == RegistrationType.VOLUNTEER) {
            VolunteerRequirement requirement = volunteerRequirementRepository.findByEventId(event.getId())
                    .orElseThrow(() -> new BusinessRuleException(
                            "Volunteer registration is not available for this event because no volunteer requirement exists.",
                            HttpStatus.BAD_REQUEST
                    ));

            if (requirement.getRequiredCount() != null) {
                long currentVolunteers = registrationRepository.countByEventIdAndTypeAndStatusIn(
                        event.getId(),
                        RegistrationType.VOLUNTEER,
                        List.of(RegistrationStatus.ACCEPTED)
                );
                if (currentVolunteers >= requirement.getRequiredCount()) {
                    throw new BusinessRuleException("Volunteer capacity has been reached for this event.", HttpStatus.CONFLICT);
                }
            }
        }

        // 8. DEFAULT REGISTRATION STATUS & registeredAt
        Registration registration = new Registration();
        registration.setUser(user);
        registration.setEvent(event);
        registration.setType(request.getType());
        registration.setStatus(RegistrationStatus.REGISTERED);
        registration.setRegisteredAt(LocalDateTime.now());
        Registration saved = registrationRepository.save(registration);
        log.info("Registration created successfully [registrationId={}, eventId={}, userId={}, type={}]",
                saved.getId(), event.getId(), user.getId(), saved.getType());
        return saved;
    }

    @Transactional
    public Registration createRegistration(Registration registration) {
        if (registration == null) {
            throw new BusinessRuleException("Registration cannot be null.", HttpStatus.BAD_REQUEST);
        }
        Long userId = registration.getUser() != null ? registration.getUser().getId() : null;
        Long eventId = registration.getEvent() != null ? registration.getEvent().getId() : null;
        RegistrationRequest request = new RegistrationRequest(userId, eventId, registration.getType());
        return createRegistration(request);
    }

    public Registration getRegistrationById(Long id) {
        if (id == null) {
            throw new BusinessRuleException("Registration ID is required.", HttpStatus.BAD_REQUEST);
        }

        Registration registration = registrationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found with id: " + id));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String && "anonymousUser".equals(auth.getPrincipal()))) {
            boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin) {
                String authEmail = auth.getName();
                boolean isOwner = registration.getUser() != null
                        && registration.getUser().getEmail() != null
                        && registration.getUser().getEmail().equalsIgnoreCase(authEmail);
                boolean isEventOrganizer = registration.getEvent() != null
                        && registration.getEvent().getOrganizer() != null
                        && registration.getEvent().getOrganizer().getEmail() != null
                        && registration.getEvent().getOrganizer().getEmail().equalsIgnoreCase(authEmail);

                if (!isOwner && !isEventOrganizer) {
                    throw new BusinessRuleException("You are not authorized to view this registration.", HttpStatus.FORBIDDEN);
                }
            }
        }

        return registration;
    }

    public List<Registration> getAllRegistrations() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String && "anonymousUser".equals(auth.getPrincipal()))) {
            boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin) {
                throw new BusinessRuleException("Only administrators can retrieve all registrations.", HttpStatus.FORBIDDEN);
            }
        }
        return registrationRepository.findAll();
    }

    public List<Registration> getRegistrationsByEvent(Long eventId) {
        return getRegistrationsByEvent(eventId, null, null);
    }

    public List<Registration> getRegistrationsByEvent(Long eventId, RegistrationType type, RegistrationStatus status) {
        validateEventAccess(
                eventId,
                "Users are not authorized to view event attendee lists.",
                "Organizers can only view attendee lists for their own events."
        );

        if (type != null && status != null) {
            return registrationRepository.findByEventIdAndTypeAndStatus(eventId, type, status);
        } else if (type != null) {
            return registrationRepository.findByEventIdAndType(eventId, type);
        } else if (status != null) {
            return registrationRepository.findByEventIdAndStatus(eventId, status);
        } else {
            return registrationRepository.findByEventId(eventId);
        }
    }

    public EventRegistrationSummaryResponse getRegistrationSummaryForEvent(Long eventId) {
        validateEventAccess(
                eventId,
                "Users are not authorized to view event registration summaries.",
                "Organizers can only view registration summaries for their own events."
        );

        long totalRegistrations = registrationRepository.countByEventId(eventId);
        long participantCount = registrationRepository.countByEventIdAndType(eventId, RegistrationType.PARTICIPANT);
        long volunteerCount = registrationRepository.countByEventIdAndType(eventId, RegistrationType.VOLUNTEER);

        long registeredCount = registrationRepository.countByEventIdAndStatus(eventId, RegistrationStatus.REGISTERED);
        long acceptedCount = registrationRepository.countByEventIdAndStatus(eventId, RegistrationStatus.ACCEPTED);
        long cancelledCount = registrationRepository.countByEventIdAndStatus(eventId, RegistrationStatus.CANCELLED);
        long rejectedCount = registrationRepository.countByEventIdAndStatus(eventId, RegistrationStatus.REJECTED);

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        statusCounts.put("REGISTERED", registeredCount);
        statusCounts.put("ACCEPTED", acceptedCount);
        statusCounts.put("CANCELLED", cancelledCount);
        statusCounts.put("REJECTED", rejectedCount);

        return new EventRegistrationSummaryResponse(
                eventId,
                totalRegistrations,
                participantCount,
                volunteerCount,
                registeredCount,
                acceptedCount,
                cancelledCount,
                rejectedCount,
                statusCounts
        );
    }

    private Event validateEventAccess(Long eventId, String userForbiddenMessage, String organizerForbiddenMessage) {
        if (eventId == null) {
            throw new BusinessRuleException("Event ID is required.", HttpStatus.BAD_REQUEST);
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String && "anonymousUser".equals(auth.getPrincipal()))) {
            boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean isOrganizer = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZER"));
            boolean isUser = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")) && !isAdmin && !isOrganizer;

            if (isUser) {
                throw new BusinessRuleException(userForbiddenMessage, HttpStatus.FORBIDDEN);
            }

            if (isOrganizer && !isAdmin) {
                String authEmail = auth.getName();
                if (event.getOrganizer() == null || event.getOrganizer().getEmail() == null
                        || !event.getOrganizer().getEmail().equalsIgnoreCase(authEmail)) {
                    throw new BusinessRuleException(organizerForbiddenMessage, HttpStatus.FORBIDDEN);
                }
            }
        }

        return event;
    }

    public List<Registration> getRegistrationsByUser(Long userId) {
        if (userId == null) {
            throw new BusinessRuleException("User ID is required.", HttpStatus.BAD_REQUEST);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String && "anonymousUser".equals(auth.getPrincipal()))) {
            boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            if (!isAdmin) {
                String authEmail = auth.getName();
                User currentUser = userRepository.findByEmail(authEmail)
                        .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found with email: " + authEmail));

                if (!currentUser.getId().equals(userId)) {
                    throw new BusinessRuleException("Users can only view their own registrations.", HttpStatus.FORBIDDEN);
                }
            }
        }

        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }

        return registrationRepository.findByUserId(userId);
    }

    public long getParticipantCount(Long eventId) {
        return registrationRepository.countByEventIdAndType(eventId, RegistrationType.PARTICIPANT);
    }

    public long getVolunteerCount(Long eventId) {
        return registrationRepository.countByEventIdAndType(eventId, RegistrationType.VOLUNTEER);
    }

    public Registration updateRegistration(Long id, Registration registration) {
        Registration existingRegistration = getRegistrationById(id);
        if (registration.getType() != null) {
            existingRegistration.setType(registration.getType());
        }
        if (registration.getStatus() != null) {
            existingRegistration.setStatus(registration.getStatus());
        }
        if (registration.getUser() != null) {
            existingRegistration.setUser(registration.getUser());
        }
        if (registration.getEvent() != null) {
            existingRegistration.setEvent(registration.getEvent());
        }
        return registrationRepository.save(existingRegistration);
    }

    public void deleteRegistration(Long id) {
        if (id == null) {
            throw new BusinessRuleException("Registration ID is required.", HttpStatus.BAD_REQUEST);
        }

        Registration registration = registrationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found with id: " + id));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String && "anonymousUser".equals(auth.getPrincipal()))) {
            boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin) {
                String authEmail = auth.getName();
                boolean isEventOrganizer = registration.getEvent() != null
                        && registration.getEvent().getOrganizer() != null
                        && registration.getEvent().getOrganizer().getEmail() != null
                        && registration.getEvent().getOrganizer().getEmail().equalsIgnoreCase(authEmail);
                if (!isEventOrganizer) {
                    throw new BusinessRuleException("Organizers can only delete registrations for events they organize.", HttpStatus.FORBIDDEN);
                }
            }
        }

        registrationRepository.delete(registration);
    }

    @Transactional
    public Registration cancelRegistration(Long id) {
        Registration registration = registrationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found with id: " + id));

        // 1. EVENT RESTRICTIONS
        Event event = registration.getEvent();
        if (event != null && (event.getStatus() == EventStatus.COMPLETED || event.getStatus() == EventStatus.CANCELLED)) {
            throw new BusinessRuleException(
                    "Cannot cancel registration for an event that is " + event.getStatus() + ".",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 2. STATUS RESTRICTIONS
        if (registration.getStatus() == RegistrationStatus.CANCELLED) {
            throw new BusinessRuleException("Registration is already cancelled", HttpStatus.CONFLICT);
        }
        if (registration.getStatus() == RegistrationStatus.REJECTED) {
            throw new BusinessRuleException("Rejected registrations cannot be cancelled", HttpStatus.CONFLICT);
        }
        if (registration.getStatus() != RegistrationStatus.REGISTERED && registration.getStatus() != RegistrationStatus.ACCEPTED) {
            throw new BusinessRuleException(
                    "Registration with status " + registration.getStatus() + " cannot be cancelled.",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 3. AUTHORIZATION / ROLE CHECKS
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String && "anonymousUser".equals(auth.getPrincipal()))) {
            boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean isOrganizer = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZER"));
            boolean isUser = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")) && !isAdmin && !isOrganizer;

            String authEmail = auth.getName();

            if (isAdmin) {
                // ADMIN can cancel any registration
            } else if (isOrganizer) {
                // ORGANIZER may cancel registrations for events they own
                if (event == null || event.getOrganizer() == null || event.getOrganizer().getEmail() == null
                        || !event.getOrganizer().getEmail().equalsIgnoreCase(authEmail)) {
                    throw new BusinessRuleException("Organizers can only cancel registrations for events they organize.", HttpStatus.FORBIDDEN);
                }
            } else if (isUser) {
                // USER can cancel only their own registration
                User user = registration.getUser();
                if (user == null || user.getEmail() == null || !user.getEmail().equalsIgnoreCase(authEmail)) {
                    throw new BusinessRuleException("Users can only cancel their own registrations.", HttpStatus.FORBIDDEN);
                }
            }
        }

        // 4. PERFORM CANCELLATION
        registration.setStatus(RegistrationStatus.CANCELLED);
        Registration saved = registrationRepository.save(registration);
        log.info("Registration cancelled successfully [registrationId={}, eventId={}]",
                saved.getId(), event != null ? event.getId() : null);
        return saved;
    }

    @Transactional
    public Registration updateVolunteerStatus(Long id, RegistrationStatus requestedStatus) {
        if (requestedStatus == null) {
            throw new BusinessRuleException("Status is required.", HttpStatus.BAD_REQUEST);
        }
        if (requestedStatus != RegistrationStatus.ACCEPTED && requestedStatus != RegistrationStatus.REJECTED) {
            throw new BusinessRuleException("Volunteer status can only be updated to ACCEPTED or REJECTED.", HttpStatus.BAD_REQUEST);
        }

        Registration registration = registrationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found with id: " + id));

        // 1. PARTICIPANT PROTECTION
        if (registration.getType() != RegistrationType.VOLUNTEER) {
            throw new BusinessRuleException("Volunteer status can only be changed for volunteer registrations.", HttpStatus.BAD_REQUEST);
        }

        // 2. EVENT STATUS RESTRICTIONS
        Event event = registration.getEvent();
        if (event != null && (event.getStatus() == EventStatus.COMPLETED || event.getStatus() == EventStatus.CANCELLED)) {
            throw new BusinessRuleException(
                    "Volunteer status cannot be updated for an event that is " + event.getStatus() + ".",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 3. AUTHORIZATION / ROLE CHECKS
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String && "anonymousUser".equals(auth.getPrincipal()))) {
            boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean isOrganizer = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZER"));
            boolean isUser = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")) && !isAdmin && !isOrganizer;

            if (isUser) {
                throw new BusinessRuleException("Users are not authorized to update volunteer status.", HttpStatus.FORBIDDEN);
            }

            if (isOrganizer && !isAdmin) {
                if (event == null || event.getOrganizer() == null || event.getOrganizer().getEmail() == null
                        || !event.getOrganizer().getEmail().equalsIgnoreCase(auth.getName())) {
                    throw new BusinessRuleException("Organizers can only update volunteer registrations for events they organize.", HttpStatus.FORBIDDEN);
                }
            }
        }

        // 4. CURRENT REGISTRATION STATUS RESTRICTIONS
        if (registration.getStatus() == RegistrationStatus.ACCEPTED) {
            throw new BusinessRuleException("Registration is already accepted", HttpStatus.CONFLICT);
        }
        if (registration.getStatus() == RegistrationStatus.REJECTED) {
            throw new BusinessRuleException("Registration is already rejected", HttpStatus.CONFLICT);
        }
        if (registration.getStatus() == RegistrationStatus.CANCELLED) {
            throw new BusinessRuleException("Cancelled registrations cannot be updated", HttpStatus.CONFLICT);
        }
        if (registration.getStatus() != RegistrationStatus.REGISTERED) {
            throw new BusinessRuleException("Only registrations with status REGISTERED can be updated.", HttpStatus.BAD_REQUEST);
        }

        // 5. ACCEPT RULES & CAPACITY
        if (requestedStatus == RegistrationStatus.ACCEPTED) {
            if (event == null) {
                throw new BusinessRuleException("Event requirement does not exist.", HttpStatus.BAD_REQUEST);
            }
            VolunteerRequirement requirement = volunteerRequirementRepository.findByEventId(event.getId())
                    .orElseThrow(() -> new BusinessRuleException(
                            "Volunteer requirement does not exist for this event.",
                            HttpStatus.BAD_REQUEST
                    ));

            if (requirement.getRequiredCount() != null) {
                long currentAccepted = registrationRepository.countByEventIdAndTypeAndStatus(
                        event.getId(),
                        RegistrationType.VOLUNTEER,
                        RegistrationStatus.ACCEPTED
                );
                if (currentAccepted >= requirement.getRequiredCount()) {
                    throw new BusinessRuleException("Volunteer capacity has been reached for this event.", HttpStatus.CONFLICT);
                }
            }
        }

        // 6. UPDATE AND SAVE
        registration.setStatus(requestedStatus);
        Registration saved = registrationRepository.save(registration);
        log.info("Volunteer status updated successfully [registrationId={}, newStatus={}]",
                saved.getId(), requestedStatus);
        return saved;
    }

    private User resolveUser(Long requestedUserId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isElevated = false;
        String authEmail = null;

        if (auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String && "anonymousUser".equals(auth.getPrincipal()))) {
            authEmail = auth.getName();
            isElevated = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_ORGANIZER"));
        }

        // Elevated roles (ADMIN / ORGANIZER) can register on behalf of a specified user
        if (isElevated && requestedUserId != null) {
            return userRepository.findById(requestedUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + requestedUserId));
        }

        // Normal USER (or elevated user with no target id specified): use authenticated user identity
        if (authEmail != null) {
            final String email = authEmail;
            return userRepository.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found with email: " + email));
        }

        // Fallback when no SecurityContext is present (e.g., direct service testing)
        if (requestedUserId != null) {
            return userRepository.findById(requestedUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + requestedUserId));
        }

        throw new BusinessRuleException("User identification is required for registration.", HttpStatus.BAD_REQUEST);
    }
}
