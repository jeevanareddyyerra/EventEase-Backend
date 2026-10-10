package com.eventease.service;

import com.eventease.dto.request.AttendanceRequest;
import com.eventease.entity.Attendance;
import com.eventease.entity.Event;
import com.eventease.entity.EventStatus;
import com.eventease.entity.Registration;
import com.eventease.entity.RegistrationStatus;
import com.eventease.entity.User;
import com.eventease.exception.BusinessRuleException;
import com.eventease.exception.ResourceNotFoundException;
import com.eventease.repository.AttendanceRepository;
import com.eventease.repository.RegistrationRepository;
import com.eventease.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);

    private final AttendanceRepository attendanceRepository;
    private final RegistrationRepository registrationRepository;
    private final UserRepository userRepository;

    public AttendanceService(
            AttendanceRepository attendanceRepository,
            RegistrationRepository registrationRepository,
            UserRepository userRepository
    ) {
        this.attendanceRepository = attendanceRepository;
        this.registrationRepository = registrationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Attendance createAttendance(AttendanceRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Attendance request cannot be null.", HttpStatus.BAD_REQUEST);
        }
        if (request.getRegistrationId() == null) {
            throw new BusinessRuleException("Registration ID is required.", HttpStatus.BAD_REQUEST);
        }
        if (request.getAttended() == null) {
            throw new BusinessRuleException("Attended status is required.", HttpStatus.BAD_REQUEST);
        }

        // 1. REGISTRATION EXISTENCE
        Registration registration = registrationRepository.findById(request.getRegistrationId())
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found with id: " + request.getRegistrationId()));

        // 2. EVENT EXISTENCE
        Event event = registration.getEvent();
        if (event == null) {
            throw new ResourceNotFoundException("Event not found for registration with id: " + registration.getId());
        }

        // 3. REGISTRATION STATUS (active: REGISTERED or ACCEPTED)
        RegistrationStatus regStatus = registration.getStatus();
        if (regStatus != RegistrationStatus.REGISTERED && regStatus != RegistrationStatus.ACCEPTED) {
            throw new BusinessRuleException("Cannot mark attendance for registration with status: " + regStatus, HttpStatus.BAD_REQUEST);
        }

        // 4. EVENT STATUS (only PUBLISHED or COMPLETED)
        if (event.getStatus() != EventStatus.PUBLISHED && event.getStatus() != EventStatus.COMPLETED) {
            throw new BusinessRuleException("Attendance can only be marked for PUBLISHED or COMPLETED events. Current event status: " + event.getStatus(), HttpStatus.BAD_REQUEST);
        }

        // 5. AUTHORIZATION (ORGANIZER own event, ADMIN any event, USER forbidden)
        validateOrganizerOrAdminAccess(event, "Only organizers and administrators can mark attendance.", "Organizers can only mark attendance for their own events.");

        // 6. PREVENT DUPLICATE ATTENDANCE
        if (attendanceRepository.existsByRegistrationId(registration.getId())) {
            throw new BusinessRuleException("Attendance has already been marked for this registration.", HttpStatus.CONFLICT);
        }

        // 7. CREATE & SAVE
        Attendance attendance = new Attendance();
        attendance.setRegistration(registration);
        attendance.setAttended(request.getAttended());
        attendance.setMarkedAt(LocalDateTime.now());
        Attendance saved = attendanceRepository.save(attendance);
        log.info("Attendance marked successfully [attendanceId={}, registrationId={}, attended={}]",
                saved.getId(), registration.getId(), saved.getAttended());
        return saved;
    }

    @Transactional
    public Attendance createAttendance(Attendance attendance) {
        if (attendance == null) {
            throw new BusinessRuleException("Attendance cannot be null.", HttpStatus.BAD_REQUEST);
        }
        Long registrationId = attendance.getRegistration() != null ? attendance.getRegistration().getId() : null;
        return createAttendance(new AttendanceRequest(registrationId, attendance.getAttended()));
    }

    public Attendance getAttendanceById(Long id) {
        return attendanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance not found with id: " + id));
    }

    public Attendance getAttendanceByRegistrationId(Long registrationId) {
        return attendanceRepository.findByRegistrationId(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance not found for registration id: " + registrationId));
    }

    public List<Attendance> getAllAttendance() {
        return attendanceRepository.findAll();
    }

    @Transactional
    public Attendance updateAttendance(Long id, AttendanceRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Attendance request cannot be null.", HttpStatus.BAD_REQUEST);
        }
        Attendance existing = getAttendanceById(id);
        Registration registration = existing.getRegistration();

        // Registration status check
        if (registration == null || registration.getStatus() == RegistrationStatus.CANCELLED || registration.getStatus() == RegistrationStatus.REJECTED) {
            throw new BusinessRuleException("Cannot update attendance for a cancelled or rejected registration.", HttpStatus.BAD_REQUEST);
        }

        // Event status check
        Event event = registration.getEvent();
        if (event != null && event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleException("Cannot update attendance for a CANCELLED event.", HttpStatus.BAD_REQUEST);
        }

        // Authorization
        validateOrganizerOrAdminAccess(event, "Only organizers and administrators can update attendance.", "Organizers can only manage attendance for their own events.");

        if (request.getAttended() != null) {
            existing.setAttended(request.getAttended());
        }
        existing.setMarkedAt(LocalDateTime.now());
        Attendance saved = attendanceRepository.save(existing);
        log.info("Attendance updated successfully [attendanceId={}, registrationId={}, attended={}]",
                saved.getId(), registration != null ? registration.getId() : null, saved.getAttended());
        return saved;
    }

    @Transactional
    public Attendance updateAttendance(Long id, Attendance attendance) {
        if (attendance == null) {
            throw new BusinessRuleException("Attendance cannot be null.", HttpStatus.BAD_REQUEST);
        }
        Long registrationId = attendance.getRegistration() != null ? attendance.getRegistration().getId() : null;
        return updateAttendance(id, new AttendanceRequest(registrationId, attendance.getAttended()));
    }

    @Transactional
    public void deleteAttendance(Long id) {
        Attendance existing = attendanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance not found with id: " + id));

        Registration registration = existing.getRegistration();
        Event event = registration != null ? registration.getEvent() : null;

        // Authorization
        validateOrganizerOrAdminAccess(event, "Only organizers and administrators can delete attendance.", "Organizers can only delete attendance for their own events.");

        // For a COMPLETED event, reject deletion
        if (event != null && event.getStatus() == EventStatus.COMPLETED) {
            throw new BusinessRuleException("Cannot delete attendance for a COMPLETED event.", HttpStatus.BAD_REQUEST);
        }

        if (registration != null) {
            registration.setAttendance(null);
        }

        attendanceRepository.delete(existing);
        log.info("Attendance deleted successfully [attendanceId={}]", id);
    }

    private void validateOrganizerOrAdminAccess(Event event, String genericForbiddenMsg, String organizerMismatchMsg) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User authUser = getAuthenticatedUser();

        if (auth != null && hasRole(auth, "ADMIN")) {
            // ADMIN allowed
            return;
        }

        if (auth != null && hasRole(auth, "ORGANIZER")) {
            if (authUser != null && event != null && event.getOrganizer() != null && !isOrganizerOwner(event.getOrganizer(), authUser)) {
                throw new BusinessRuleException(organizerMismatchMsg, HttpStatus.FORBIDDEN);
            }
            return;
        }

        // USER or unprivileged
        throw new BusinessRuleException(genericForbiddenMsg, HttpStatus.FORBIDDEN);
    }

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String && "anonymousUser".equals(auth.getPrincipal()))) {
            return userRepository.findByEmail(auth.getName()).orElse(null);
        }
        return null;
    }

    private boolean hasRole(Authentication auth, String role) {
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role) || a.getAuthority().equals("ROLE_" + role));
    }

    private boolean isOrganizerOwner(User organizer, User authUser) {
        if (organizer == null || authUser == null) return false;
        if (organizer.getId() != null && authUser.getId() != null && organizer.getId().equals(authUser.getId())) {
            return true;
        }
        return organizer.getEmail() != null && organizer.getEmail().equalsIgnoreCase(authUser.getEmail());
    }
}
