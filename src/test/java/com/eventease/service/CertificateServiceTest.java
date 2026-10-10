package com.eventease.service;

import com.eventease.dto.request.CertificateRequest;
import com.eventease.entity.*;
import com.eventease.exception.BusinessRuleException;
import com.eventease.exception.ResourceNotFoundException;
import com.eventease.repository.AttendanceRepository;
import com.eventease.repository.CertificateRepository;
import com.eventease.repository.RegistrationRepository;
import com.eventease.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CertificateService certificateService;

    private User participantUser;
    private User organizerUser;
    private User adminUser;
    private Event completedEvent;
    private Registration participantReg;
    private Attendance attendedRecord;

    @BeforeEach
    void setUp() {
        participantUser = new User();
        participantUser.setId(1L);
        participantUser.setName("Participant One");
        participantUser.setEmail("participant@example.com");
        participantUser.setRole(Role.USER);

        organizerUser = new User();
        organizerUser.setId(2L);
        organizerUser.setName("Organizer One");
        organizerUser.setEmail("organizer@example.com");
        organizerUser.setRole(Role.ORGANIZER);

        adminUser = new User();
        adminUser.setId(3L);
        adminUser.setName("Admin One");
        adminUser.setEmail("admin@example.com");
        adminUser.setRole(Role.ADMIN);

        completedEvent = new Event();
        completedEvent.setId(10L);
        completedEvent.setTitle("Hackathon 2026");
        completedEvent.setStatus(EventStatus.COMPLETED);
        completedEvent.setOrganizer(organizerUser);

        participantReg = new Registration();
        participantReg.setId(100L);
        participantReg.setUser(participantUser);
        participantReg.setEvent(completedEvent);
        participantReg.setType(RegistrationType.PARTICIPANT);
        participantReg.setStatus(RegistrationStatus.REGISTERED);

        attendedRecord = new Attendance(participantReg, true);
        attendedRecord.setId(500L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setupSecurityContext(String email, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                email,
                null,
                Collections.singletonList(new SimpleGrantedAuthority(role))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // 22. certificate issued for attended completed event -> success
    @Test
    void certificateIssuedForAttendedCompletedEvent_success() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerUser));
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));
        when(attendanceRepository.findByRegistrationId(100L)).thenReturn(Optional.of(attendedRecord));
        when(certificateRepository.existsByRegistrationId(100L)).thenReturn(false);
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(inv -> inv.getArgument(0));

        CertificateRequest request = new CertificateRequest(100L, "https://eventease.com/certs/cert123.pdf");
        Certificate certificate = certificateService.createCertificate(request);

        assertNotNull(certificate);
        assertEquals(participantReg, certificate.getRegistration());
        assertEquals(CertificateStatus.ISSUED, certificate.getStatus());
        assertNotNull(certificate.getIssuedAt());
        assertEquals("https://eventease.com/certs/cert123.pdf", certificate.getCertificateUrl());
    }

    // 23. certificate for non-attended registration -> 400
    @Test
    void certificateForNonAttendedRegistration_throws400() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        Attendance nonAttended = new Attendance(participantReg, false);

        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));
        when(attendanceRepository.findByRegistrationId(100L)).thenReturn(Optional.of(nonAttended));

        CertificateRequest request = new CertificateRequest(100L, "https://eventease.com/certs/cert.pdf");
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> certificateService.createCertificate(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("did not attend"));
    }

    // 24. certificate for CANCELLED registration -> 400
    @Test
    void certificateForCancelledRegistration_throws400() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        participantReg.setStatus(RegistrationStatus.CANCELLED);
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));

        CertificateRequest request = new CertificateRequest(100L, "https://eventease.com/certs/cert.pdf");
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> certificateService.createCertificate(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("CANCELLED"));
    }

    // 25. certificate for REJECTED registration -> 400
    @Test
    void certificateForRejectedRegistration_throws400() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        participantReg.setStatus(RegistrationStatus.REJECTED);
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));

        CertificateRequest request = new CertificateRequest(100L, "https://eventease.com/certs/cert.pdf");
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> certificateService.createCertificate(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("REJECTED"));
    }

    // 26. certificate for REGISTERED/ACCEPTED but no attendance -> 400
    @Test
    void certificateForRegisteredOrAcceptedButNoAttendance_throws400() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));
        when(attendanceRepository.findByRegistrationId(100L)).thenReturn(Optional.empty());

        CertificateRequest request = new CertificateRequest(100L, "https://eventease.com/certs/cert.pdf");
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> certificateService.createCertificate(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("no attendance record"));
    }

    // 27. certificate for PUBLISHED event -> 400
    @Test
    void certificateForPublishedEvent_throws400() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        completedEvent.setStatus(EventStatus.PUBLISHED);
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));

        CertificateRequest request = new CertificateRequest(100L, "https://eventease.com/certs/cert.pdf");
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> certificateService.createCertificate(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("COMPLETED"));
    }

    // 28. certificate for COMPLETED event -> success
    @Test
    void certificateForCompletedEvent_success() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerUser));
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));
        when(attendanceRepository.findByRegistrationId(100L)).thenReturn(Optional.of(attendedRecord));
        when(certificateRepository.existsByRegistrationId(100L)).thenReturn(false);
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(inv -> inv.getArgument(0));

        Certificate cert = certificateService.createCertificate(new CertificateRequest(100L));
        assertNotNull(cert);
        assertEquals(CertificateStatus.ISSUED, cert.getStatus());
    }

    // 29. certificate for CANCELLED event -> 400
    @Test
    void certificateForCancelledEvent_throws400() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        completedEvent.setStatus(EventStatus.CANCELLED);
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));

        CertificateRequest request = new CertificateRequest(100L, "https://eventease.com/certs/cert.pdf");
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> certificateService.createCertificate(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    // 30. USER issuing certificate -> 403
    @Test
    void userIssuingCertificate_throws403() {
        setupSecurityContext("participant@example.com", "ROLE_USER");
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));
        when(attendanceRepository.findByRegistrationId(100L)).thenReturn(Optional.of(attendedRecord));

        CertificateRequest request = new CertificateRequest(100L, "https://eventease.com/certs/cert.pdf");
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> certificateService.createCertificate(request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    // 31. organizer own event -> success
    @Test
    void organizerOwnEvent_success() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerUser));
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));
        when(attendanceRepository.findByRegistrationId(100L)).thenReturn(Optional.of(attendedRecord));
        when(certificateRepository.existsByRegistrationId(100L)).thenReturn(false);
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(inv -> inv.getArgument(0));

        Certificate cert = certificateService.createCertificate(new CertificateRequest(100L, "url"));
        assertNotNull(cert);
    }

    // 32. organizer another organizer's event -> 403
    @Test
    void organizerAnotherOrganizersEvent_throws403() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerUser));

        User foreignOrganizer = new User();
        foreignOrganizer.setId(99L);
        foreignOrganizer.setEmail("foreign@example.com");
        completedEvent.setOrganizer(foreignOrganizer);

        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));
        when(attendanceRepository.findByRegistrationId(100L)).thenReturn(Optional.of(attendedRecord));

        CertificateRequest request = new CertificateRequest(100L, "url");
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> certificateService.createCertificate(request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    // 33. ADMIN any event -> success
    @Test
    void adminAnyEvent_success() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));
        when(attendanceRepository.findByRegistrationId(100L)).thenReturn(Optional.of(attendedRecord));
        when(certificateRepository.existsByRegistrationId(100L)).thenReturn(false);
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(inv -> inv.getArgument(0));

        Certificate cert = certificateService.createCertificate(new CertificateRequest(100L, "admin_url"));
        assertNotNull(cert);
    }

    // 34. duplicate certificate -> 409
    @Test
    void duplicateCertificate_throws409() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerUser));
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));
        when(attendanceRepository.findByRegistrationId(100L)).thenReturn(Optional.of(attendedRecord));
        when(certificateRepository.existsByRegistrationId(100L)).thenReturn(true);

        CertificateRequest request = new CertificateRequest(100L, "url");
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> certificateService.createCertificate(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("already been issued"));
    }

    // 35. issuedAt automatically populated
    @Test
    void issuedAtAutomaticallyPopulated() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));
        when(attendanceRepository.findByRegistrationId(100L)).thenReturn(Optional.of(attendedRecord));
        when(certificateRepository.existsByRegistrationId(100L)).thenReturn(false);
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(inv -> inv.getArgument(0));

        Certificate cert = certificateService.createCertificate(new CertificateRequest(100L));
        assertNotNull(cert.getIssuedAt());
    }

    // 36. status automatically set to ISSUED
    @Test
    void statusAutomaticallySetToIssued() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));
        when(attendanceRepository.findByRegistrationId(100L)).thenReturn(Optional.of(attendedRecord));
        when(certificateRepository.existsByRegistrationId(100L)).thenReturn(false);
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(inv -> inv.getArgument(0));

        Certificate cert = certificateService.createCertificate(new CertificateRequest(100L));
        assertEquals(CertificateStatus.ISSUED, cert.getStatus());
    }

    // 37. certificate URL preserved
    @Test
    void certificateUrlPreserved() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));
        when(attendanceRepository.findByRegistrationId(100L)).thenReturn(Optional.of(attendedRecord));
        when(certificateRepository.existsByRegistrationId(100L)).thenReturn(false);
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(inv -> inv.getArgument(0));

        Certificate cert = certificateService.createCertificate(new CertificateRequest(100L, "https://storage.eventease.com/certs/c1.pdf"));
        assertEquals("https://storage.eventease.com/certs/c1.pdf", cert.getCertificateUrl());
    }

    // 38. certificate deletion authorization works
    @Test
    void certificateDeletionAuthorizationWorks() {
        setupSecurityContext("participant@example.com", "ROLE_USER");
        Certificate cert = new Certificate(participantReg, CertificateStatus.ISSUED, "url");
        cert.setId(200L);
        when(certificateRepository.findById(200L)).thenReturn(Optional.of(cert));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> certificateService.deleteCertificate(200L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    // 39. certificate deletion from finalized COMPLETED event follows business rule (throws 400)
    @Test
    void certificateDeletionFromFinalizedCompletedEvent_throws400() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");
        Certificate cert = new Certificate(participantReg, CertificateStatus.ISSUED, "url");
        cert.setId(200L);
        when(certificateRepository.findById(200L)).thenReturn(Optional.of(cert));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> certificateService.deleteCertificate(200L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("COMPLETED"));
    }

    // 40. deleting certificate does not delete attendance
    @Test
    void deletingCertificateDoesNotDeleteAttendance() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");

        Event publishedEvt = new Event();
        publishedEvt.setId(20L);
        publishedEvt.setStatus(EventStatus.PUBLISHED);

        Registration pubReg = new Registration();
        pubReg.setId(201L);
        pubReg.setEvent(publishedEvt);

        Certificate cert = new Certificate(pubReg, CertificateStatus.ISSUED, "url");
        cert.setId(200L);
        when(certificateRepository.findById(200L)).thenReturn(Optional.of(cert));

        certificateService.deleteCertificate(200L);
        verify(certificateRepository).delete(cert);
        verifyNoInteractions(attendanceRepository);
    }

    // 41. deleting certificate does not delete registration
    @Test
    void deletingCertificateDoesNotDeleteRegistration() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");

        Event publishedEvt = new Event();
        publishedEvt.setId(20L);
        publishedEvt.setStatus(EventStatus.PUBLISHED);

        Registration pubReg = new Registration();
        pubReg.setId(201L);
        pubReg.setEvent(publishedEvt);

        Certificate cert = new Certificate(pubReg, CertificateStatus.ISSUED, "url");
        cert.setId(200L);
        when(certificateRepository.findById(200L)).thenReturn(Optional.of(cert));

        certificateService.deleteCertificate(200L);
        verify(certificateRepository).delete(cert);
        verifyNoInteractions(registrationRepository);
    }
}
