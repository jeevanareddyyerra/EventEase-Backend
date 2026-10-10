package com.eventease.service;

import com.eventease.dto.request.CertificateRequest;
import com.eventease.entity.Attendance;
import com.eventease.entity.Certificate;
import com.eventease.entity.CertificateStatus;
import com.eventease.entity.Event;
import com.eventease.entity.EventStatus;
import com.eventease.entity.Registration;
import com.eventease.entity.RegistrationStatus;
import com.eventease.entity.User;
import com.eventease.exception.BusinessRuleException;
import com.eventease.exception.ResourceNotFoundException;
import com.eventease.repository.AttendanceRepository;
import com.eventease.repository.CertificateRepository;
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
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);

    private final CertificateRepository certificateRepository;
    private final RegistrationRepository registrationRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;

    public CertificateService(
            CertificateRepository certificateRepository,
            RegistrationRepository registrationRepository,
            AttendanceRepository attendanceRepository,
            UserRepository userRepository
    ) {
        this.certificateRepository = certificateRepository;
        this.registrationRepository = registrationRepository;
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Certificate createCertificate(CertificateRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Certificate request cannot be null.", HttpStatus.BAD_REQUEST);
        }
        if (request.getRegistrationId() == null) {
            throw new BusinessRuleException("Registration ID is required.", HttpStatus.BAD_REQUEST);
        }

        // 1. REGISTRATION EXISTENCE
        Registration registration = registrationRepository.findById(request.getRegistrationId())
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found with id: " + request.getRegistrationId()));

        // 2. EVENT EXISTENCE
        Event event = registration.getEvent();
        if (event == null) {
            throw new ResourceNotFoundException("Event not found for registration with id: " + registration.getId());
        }

        // 3. REGISTRATION STATUS (not CANCELLED/REJECTED, must be REGISTERED or ACCEPTED)
        RegistrationStatus regStatus = registration.getStatus();
        if (regStatus == RegistrationStatus.CANCELLED || regStatus == RegistrationStatus.REJECTED) {
            throw new BusinessRuleException("Cannot issue certificate for a " + regStatus + " registration.", HttpStatus.BAD_REQUEST);
        }
        if (regStatus != RegistrationStatus.REGISTERED && regStatus != RegistrationStatus.ACCEPTED) {
            throw new BusinessRuleException("Cannot issue certificate for registration with status: " + regStatus, HttpStatus.BAD_REQUEST);
        }

        // 4. EVENT STATUS (only COMPLETED)
        if (event.getStatus() != EventStatus.COMPLETED) {
            throw new BusinessRuleException("Certificates can only be issued for COMPLETED events. Current status: " + event.getStatus(), HttpStatus.BAD_REQUEST);
        }

        // 5. ATTENDANCE VERIFICATION (must have attendance record AND attended == true)
        Attendance attendance = attendanceRepository.findByRegistrationId(registration.getId())
                .orElseThrow(() -> new BusinessRuleException("Cannot issue certificate: Registration has no attendance record.", HttpStatus.BAD_REQUEST));
        if (attendance.getAttended() == null || !attendance.getAttended()) {
            throw new BusinessRuleException("Cannot issue certificate: Attendee did not attend the event.", HttpStatus.BAD_REQUEST);
        }

        // 6. AUTHORIZATION (ORGANIZER own event, ADMIN any event, USER forbidden)
        validateOrganizerOrAdminAccess(event, "Only organizers and administrators can issue certificates.", "Organizers can only issue certificates for their own events.");

        // 7. PREVENT DUPLICATE CERTIFICATE
        if (certificateRepository.existsByRegistrationId(registration.getId())) {
            throw new BusinessRuleException("Certificate has already been issued for this registration.", HttpStatus.CONFLICT);
        }

        // 8. SET ISSUED STATUS & TIMESTAMP & PRESERVE URL
        Certificate certificate = new Certificate();
        certificate.setRegistration(registration);
        certificate.setStatus(CertificateStatus.ISSUED);
        certificate.setIssuedAt(LocalDateTime.now());
        certificate.setCertificateUrl(request.getCertificateUrl());
        Certificate saved = certificateRepository.save(certificate);
        log.info("Certificate issued successfully [certificateId={}, registrationId={}, eventId={}]",
                saved.getId(), registration.getId(), event.getId());
        return saved;
    }

    @Transactional
    public Certificate createCertificate(Certificate certificate) {
        if (certificate == null) {
            throw new BusinessRuleException("Certificate cannot be null.", HttpStatus.BAD_REQUEST);
        }
        Long registrationId = certificate.getRegistration() != null ? certificate.getRegistration().getId() : null;
        return createCertificate(new CertificateRequest(registrationId, certificate.getStatus(), certificate.getCertificateUrl(), certificate.getIssuedAt()));
    }

    public Certificate getCertificateById(Long id) {
        return certificateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found with id: " + id));
    }

    public Certificate getCertificateByRegistrationId(Long registrationId) {
        return certificateRepository.findByRegistrationId(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found for registration id: " + registrationId));
    }

    public List<Certificate> getAllCertificates() {
        return certificateRepository.findAll();
    }

    @Transactional
    public Certificate updateCertificate(Long id, CertificateRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Certificate request cannot be null.", HttpStatus.BAD_REQUEST);
        }
        Certificate existing = getCertificateById(id);
        Registration registration = existing.getRegistration();

        // Registration check
        if (registration == null || registration.getStatus() == RegistrationStatus.CANCELLED || registration.getStatus() == RegistrationStatus.REJECTED) {
            throw new BusinessRuleException("Cannot update certificate for a cancelled or rejected registration.", HttpStatus.BAD_REQUEST);
        }

        // Event check
        Event event = registration.getEvent();
        if (event != null && event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleException("Cannot update certificate for a CANCELLED event.", HttpStatus.BAD_REQUEST);
        }

        // Authorization
        validateOrganizerOrAdminAccess(event, "Only organizers and administrators can update certificates.", "Organizers can only manage certificates for their own events.");

        if (request.getCertificateUrl() != null) {
            existing.setCertificateUrl(request.getCertificateUrl());
        }
        if (request.getStatus() != null) {
            existing.setStatus(request.getStatus());
        }
        return certificateRepository.save(existing);
    }

    @Transactional
    public Certificate updateCertificate(Long id, Certificate certificate) {
        if (certificate == null) {
            throw new BusinessRuleException("Certificate cannot be null.", HttpStatus.BAD_REQUEST);
        }
        Long registrationId = certificate.getRegistration() != null ? certificate.getRegistration().getId() : null;
        return updateCertificate(id, new CertificateRequest(registrationId, certificate.getStatus(), certificate.getCertificateUrl(), certificate.getIssuedAt()));
    }

    @Transactional
    public void deleteCertificate(Long id) {
        Certificate existing = certificateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found with id: " + id));

        Registration registration = existing.getRegistration();
        Event event = registration != null ? registration.getEvent() : null;

        // Authorization
        validateOrganizerOrAdminAccess(event, "Only organizers and administrators can delete certificates.", "Organizers can only delete certificates for their own events.");

        // For finalized COMPLETED event, restrict deletion
        if (event != null && event.getStatus() == EventStatus.COMPLETED) {
            throw new BusinessRuleException("Cannot delete certificate for a finalized COMPLETED event.", HttpStatus.BAD_REQUEST);
        }

        if (registration != null) {
            registration.setCertificate(null);
        }

        certificateRepository.delete(existing);
        log.info("Certificate deleted successfully [certificateId={}]", id);
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
