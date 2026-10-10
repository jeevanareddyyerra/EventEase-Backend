package com.eventease.service;

import com.eventease.dto.request.AttendanceRequest;
import com.eventease.entity.*;
import com.eventease.exception.BusinessRuleException;
import com.eventease.exception.ResourceNotFoundException;
import com.eventease.repository.AttendanceRepository;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AttendanceService attendanceService;

    private User participantUser;
    private User organizerUser;
    private User adminUser;
    private Event publishedEvent;
    private Registration participantReg;

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

        publishedEvent = new Event();
        publishedEvent.setId(10L);
        publishedEvent.setTitle("Hackathon 2026");
        publishedEvent.setStatus(EventStatus.PUBLISHED);
        publishedEvent.setOrganizer(organizerUser);

        participantReg = new Registration();
        participantReg.setId(100L);
        participantReg.setUser(participantUser);
        participantReg.setEvent(publishedEvent);
        participantReg.setType(RegistrationType.PARTICIPANT);
        participantReg.setStatus(RegistrationStatus.REGISTERED);
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

    // 1. valid participant attendance -> success
    @Test
    void validParticipantAttendance_success() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerUser));
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));
        when(attendanceRepository.existsByRegistrationId(100L)).thenReturn(false);
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendanceRequest request = new AttendanceRequest(100L, true);
        Attendance created = attendanceService.createAttendance(request);

        assertNotNull(created);
        assertEquals(participantReg, created.getRegistration());
        assertTrue(created.getAttended());
        assertNotNull(created.getMarkedAt());
        verify(attendanceRepository).save(any(Attendance.class));
    }

    // 2. valid volunteer attendance -> success
    @Test
    void validVolunteerAttendance_success() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerUser));

        Registration volunteerReg = new Registration();
        volunteerReg.setId(101L);
        volunteerReg.setUser(participantUser);
        volunteerReg.setEvent(publishedEvent);
        volunteerReg.setType(RegistrationType.VOLUNTEER);
        volunteerReg.setStatus(RegistrationStatus.ACCEPTED);

        when(registrationRepository.findById(101L)).thenReturn(Optional.of(volunteerReg));
        when(attendanceRepository.existsByRegistrationId(101L)).thenReturn(false);
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendanceRequest request = new AttendanceRequest(101L, true);
        Attendance created = attendanceService.createAttendance(request);

        assertNotNull(created);
        assertEquals(volunteerReg, created.getRegistration());
        assertTrue(created.getAttended());
    }

    // 3. CANCELLED registration -> 400
    @Test
    void cancelledRegistration_throws400() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        participantReg.setStatus(RegistrationStatus.CANCELLED);
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));

        AttendanceRequest request = new AttendanceRequest(100L, true);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> attendanceService.createAttendance(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("CANCELLED"));
    }

    // 4. REJECTED registration -> 400
    @Test
    void rejectedRegistration_throws400() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        participantReg.setStatus(RegistrationStatus.REJECTED);
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));

        AttendanceRequest request = new AttendanceRequest(100L, true);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> attendanceService.createAttendance(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("REJECTED"));
    }

    // 5. missing registration -> 404
    @Test
    void missingRegistration_throws404() {
        when(registrationRepository.findById(999L)).thenReturn(Optional.empty());

        AttendanceRequest request = new AttendanceRequest(999L, true);
        assertThrows(ResourceNotFoundException.class, () -> attendanceService.createAttendance(request));
    }

    // 6. PENDING event -> 400
    @Test
    void pendingEvent_throws400() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        publishedEvent.setStatus(EventStatus.PENDING);
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));

        AttendanceRequest request = new AttendanceRequest(100L, true);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> attendanceService.createAttendance(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("PENDING"));
    }

    // 7. APPROVED event -> 400
    @Test
    void approvedEvent_throws400() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        publishedEvent.setStatus(EventStatus.APPROVED);
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));

        AttendanceRequest request = new AttendanceRequest(100L, true);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> attendanceService.createAttendance(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("APPROVED"));
    }

    // 8. PUBLISHED event -> success
    @Test
    void publishedEvent_success() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        publishedEvent.setStatus(EventStatus.PUBLISHED);
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerUser));
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));
        when(attendanceRepository.existsByRegistrationId(100L)).thenReturn(false);
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendanceRequest request = new AttendanceRequest(100L, true);
        Attendance created = attendanceService.createAttendance(request);
        assertNotNull(created);
    }

    // 9. COMPLETED event -> success
    @Test
    void completedEvent_success() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        publishedEvent.setStatus(EventStatus.COMPLETED);
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerUser));
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));
        when(attendanceRepository.existsByRegistrationId(100L)).thenReturn(false);
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendanceRequest request = new AttendanceRequest(100L, true);
        Attendance created = attendanceService.createAttendance(request);
        assertNotNull(created);
    }

    // 10. CANCELLED event -> 400
    @Test
    void cancelledEvent_throws400() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        publishedEvent.setStatus(EventStatus.CANCELLED);
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));

        AttendanceRequest request = new AttendanceRequest(100L, true);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> attendanceService.createAttendance(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("CANCELLED"));
    }

    // 11. USER marking attendance -> 403
    @Test
    void userMarkingAttendance_throws403() {
        setupSecurityContext("participant@example.com", "ROLE_USER");
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));

        AttendanceRequest request = new AttendanceRequest(100L, true);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> attendanceService.createAttendance(request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    // 12. organizer own event -> success
    @Test
    void organizerOwnEvent_success() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerUser));
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));
        when(attendanceRepository.existsByRegistrationId(100L)).thenReturn(false);
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));

        Attendance created = attendanceService.createAttendance(new AttendanceRequest(100L, true));
        assertNotNull(created);
    }

    // 13. organizer another organizer's event -> 403
    @Test
    void organizerAnotherOrganizersEvent_throws403() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerUser));

        User otherOrganizer = new User();
        otherOrganizer.setId(99L);
        otherOrganizer.setEmail("other@example.com");
        publishedEvent.setOrganizer(otherOrganizer);

        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));

        AttendanceRequest request = new AttendanceRequest(100L, true);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> attendanceService.createAttendance(request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    // 14. ADMIN any event -> success
    @Test
    void adminAnyEvent_success() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));
        when(attendanceRepository.existsByRegistrationId(100L)).thenReturn(false);
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));

        Attendance created = attendanceService.createAttendance(new AttendanceRequest(100L, true));
        assertNotNull(created);
    }

    // 15. duplicate attendance -> 409
    @Test
    void duplicateAttendance_throws409() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerUser));
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));
        when(attendanceRepository.existsByRegistrationId(100L)).thenReturn(true);

        AttendanceRequest request = new AttendanceRequest(100L, true);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> attendanceService.createAttendance(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("already been marked"));
    }

    // 16. update attendance -> success
    @Test
    void updateAttendance_success() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerUser));

        Attendance attendance = new Attendance(participantReg, true);
        attendance.setId(50L);

        when(attendanceRepository.findById(50L)).thenReturn(Optional.of(attendance));
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));

        Attendance updated = attendanceService.updateAttendance(50L, new AttendanceRequest(false));
        assertNotNull(updated);
        assertFalse(updated.getAttended());
    }

    // 17. update attendance for invalid registration -> 400
    @Test
    void updateAttendanceForInvalidRegistration_throws400() {
        participantReg.setStatus(RegistrationStatus.CANCELLED);
        Attendance attendance = new Attendance(participantReg, true);
        attendance.setId(50L);

        when(attendanceRepository.findById(50L)).thenReturn(Optional.of(attendance));

        AttendanceRequest request = new AttendanceRequest(false);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> attendanceService.updateAttendance(50L, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    // 18. false attendance is preserved, not deleted
    @Test
    void falseAttendanceIsPreservedNotDeleted() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerUser));
        when(registrationRepository.findById(100L)).thenReturn(Optional.of(participantReg));
        when(attendanceRepository.existsByRegistrationId(100L)).thenReturn(false);
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));

        Attendance created = attendanceService.createAttendance(new AttendanceRequest(100L, false));
        assertNotNull(created);
        assertFalse(created.getAttended());
        verify(attendanceRepository, never()).delete(any());
    }

    // 19. deleting attendance from PUBLISHED event -> allowed according to rules
    @Test
    void deletingAttendanceFromPublishedEvent_allowed() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerUser));

        Attendance attendance = new Attendance(participantReg, true);
        attendance.setId(50L);
        when(attendanceRepository.findById(50L)).thenReturn(Optional.of(attendance));

        attendanceService.deleteAttendance(50L);
        verify(attendanceRepository).delete(attendance);
    }

    // 20. deleting attendance from COMPLETED event -> 400
    @Test
    void deletingAttendanceFromCompletedEvent_throws400() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerUser));

        publishedEvent.setStatus(EventStatus.COMPLETED);
        Attendance attendance = new Attendance(participantReg, true);
        attendance.setId(50L);
        when(attendanceRepository.findById(50L)).thenReturn(Optional.of(attendance));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> attendanceService.deleteAttendance(50L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("COMPLETED"));
    }

    // 21. deleting attendance does not delete registration
    @Test
    void deletingAttendanceDoesNotDeleteRegistration() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");

        Attendance attendance = new Attendance(participantReg, true);
        attendance.setId(50L);
        when(attendanceRepository.findById(50L)).thenReturn(Optional.of(attendance));

        attendanceService.deleteAttendance(50L);
        verify(attendanceRepository).delete(attendance);
        verifyNoInteractions(registrationRepository);
    }
}
