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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VolunteerRequirementRepository volunteerRequirementRepository;

    @InjectMocks
    private RegistrationService registrationService;

    private User authUser;
    private Event publishedEvent;

    @BeforeEach
    void setUp() {
        authUser = new User();
        authUser.setId(1L);
        authUser.setName("Test User");
        authUser.setEmail("test@example.com");
        authUser.setRole(Role.USER);

        User organizer = new User();
        organizer.setId(2L);
        organizer.setName("Event Organizer");
        organizer.setEmail("organizer@example.com");
        organizer.setRole(Role.ORGANIZER);

        publishedEvent = new Event();
        publishedEvent.setId(10L);
        publishedEvent.setTitle("Annual Conference");
        publishedEvent.setStatus(EventStatus.PUBLISHED);
        publishedEvent.setCapacity(50);
        publishedEvent.setRegistrationDeadline(LocalDateTime.now().plusDays(2));
        publishedEvent.setOrganizer(organizer);
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

    @Test
    void validParticipantRegistration_success() {
        setupSecurityContext("test@example.com", "ROLE_USER");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(authUser));
        when(registrationRepository.existsByUserIdAndEventId(1L, 10L)).thenReturn(false);
        when(registrationRepository.countByEventIdAndTypeAndStatusIn(eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()))
                .thenReturn(5L);
        when(registrationRepository.save(any(Registration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationRequest request = new RegistrationRequest(1L, 10L, RegistrationType.PARTICIPANT);
        Registration result = registrationService.createRegistration(request);

        assertNotNull(result);
        assertEquals(authUser, result.getUser());
        assertEquals(publishedEvent, result.getEvent());
        assertEquals(RegistrationType.PARTICIPANT, result.getType());
        assertEquals(RegistrationStatus.REGISTERED, result.getStatus());
        assertNotNull(result.getRegisteredAt());
        verify(registrationRepository).save(any(Registration.class));
    }

    @Test
    void validVolunteerRegistration_success() {
        setupSecurityContext("test@example.com", "ROLE_USER");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(authUser));
        when(registrationRepository.existsByUserIdAndEventId(1L, 10L)).thenReturn(false);

        VolunteerRequirement requirement = new VolunteerRequirement(publishedEvent, 5, "Volunteers needed");
        when(volunteerRequirementRepository.findByEventId(10L)).thenReturn(Optional.of(requirement));
        when(registrationRepository.countByEventIdAndTypeAndStatusIn(eq(10L), eq(RegistrationType.VOLUNTEER), anyCollection()))
                .thenReturn(2L);
        when(registrationRepository.save(any(Registration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationRequest request = new RegistrationRequest(1L, 10L, RegistrationType.VOLUNTEER);
        Registration result = registrationService.createRegistration(request);

        assertNotNull(result);
        assertEquals(RegistrationType.VOLUNTEER, result.getType());
        assertEquals(RegistrationStatus.REGISTERED, result.getStatus());
        assertNotNull(result.getRegisteredAt());
    }

    @Test
    void eventNotFound_throwsResourceNotFoundException() {
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());

        RegistrationRequest request = new RegistrationRequest(1L, 999L, RegistrationType.PARTICIPANT);
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                registrationService.createRegistration(request)
        );
        assertTrue(ex.getMessage().contains("Event not found with id: 999"));
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"PENDING", "APPROVED", "COMPLETED", "CANCELLED"})
    void registrationForNonPublishedEvent_throwsException(EventStatus nonPublishedStatus) {
        setupSecurityContext("test@example.com", "ROLE_USER");
        publishedEvent.setStatus(nonPublishedStatus);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(authUser));

        RegistrationRequest request = new RegistrationRequest(1L, 10L, RegistrationType.PARTICIPANT);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.createRegistration(request)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Only PUBLISHED events allow registration"));
    }

    @Test
    void registrationAfterDeadline_throwsException() {
        setupSecurityContext("test@example.com", "ROLE_USER");
        publishedEvent.setRegistrationDeadline(LocalDateTime.now().minusHours(1));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(authUser));

        RegistrationRequest request = new RegistrationRequest(1L, 10L, RegistrationType.PARTICIPANT);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.createRegistration(request)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Registration deadline has passed"));
    }

    @Test
    void duplicateRegistration_throwsException() {
        setupSecurityContext("test@example.com", "ROLE_USER");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(authUser));
        when(registrationRepository.existsByUserIdAndEventId(1L, 10L)).thenReturn(true);

        RegistrationRequest request = new RegistrationRequest(1L, 10L, RegistrationType.PARTICIPANT);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.createRegistration(request)
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("already registered"));
    }

    @Test
    void participantCapacityReached_throwsException() {
        setupSecurityContext("test@example.com", "ROLE_USER");
        publishedEvent.setCapacity(20);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(authUser));
        when(registrationRepository.existsByUserIdAndEventId(1L, 10L)).thenReturn(false);
        when(registrationRepository.countByEventIdAndTypeAndStatusIn(eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()))
                .thenReturn(20L);

        RegistrationRequest request = new RegistrationRequest(1L, 10L, RegistrationType.PARTICIPANT);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.createRegistration(request)
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Participant capacity has been reached"));
    }

    @Test
    void volunteerCapacityReached_throwsException() {
        setupSecurityContext("test@example.com", "ROLE_USER");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(authUser));
        when(registrationRepository.existsByUserIdAndEventId(1L, 10L)).thenReturn(false);

        VolunteerRequirement requirement = new VolunteerRequirement(publishedEvent, 3, "Volunteers needed");
        when(volunteerRequirementRepository.findByEventId(10L)).thenReturn(Optional.of(requirement));
        when(registrationRepository.countByEventIdAndTypeAndStatusIn(eq(10L), eq(RegistrationType.VOLUNTEER), anyCollection()))
                .thenReturn(3L);

        RegistrationRequest request = new RegistrationRequest(1L, 10L, RegistrationType.VOLUNTEER);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.createRegistration(request)
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Volunteer capacity has been reached"));
    }

    @Test
    void volunteerRegistrationWhenNoVolunteerRequirement_throwsException() {
        setupSecurityContext("test@example.com", "ROLE_USER");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(authUser));
        when(registrationRepository.existsByUserIdAndEventId(1L, 10L)).thenReturn(false);
        when(volunteerRequirementRepository.findByEventId(10L)).thenReturn(Optional.empty());

        RegistrationRequest request = new RegistrationRequest(1L, 10L, RegistrationType.VOLUNTEER);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.createRegistration(request)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("no volunteer requirement exists"));
    }

    @Test
    void cancelledAndRejectedRegistrationsDoNotConsumeCapacity() {
        setupSecurityContext("test@example.com", "ROLE_USER");
        publishedEvent.setCapacity(2);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(authUser));
        when(registrationRepository.existsByUserIdAndEventId(1L, 10L)).thenReturn(false);

        // Active statuses are only REGISTERED and ACCEPTED
        // Suppose there are 5 registrations in DB, but only 1 is active (the rest are CANCELLED/REJECTED)
        when(registrationRepository.countByEventIdAndTypeAndStatusIn(
                eq(10L),
                eq(RegistrationType.PARTICIPANT),
                eq(List.of(RegistrationStatus.REGISTERED, RegistrationStatus.ACCEPTED))
        )).thenReturn(1L);

        when(registrationRepository.save(any(Registration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationRequest request = new RegistrationRequest(1L, 10L, RegistrationType.PARTICIPANT);
        Registration result = registrationService.createRegistration(request);

        assertNotNull(result);
        assertEquals(RegistrationStatus.REGISTERED, result.getStatus());
    }

    @Test
    void userCannotRegisterUsingAnotherUserId() {
        // Authenticated as test@example.com (id: 1L)
        setupSecurityContext("test@example.com", "ROLE_USER");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(authUser));
        when(registrationRepository.existsByUserIdAndEventId(1L, 10L)).thenReturn(false);
        when(registrationRepository.countByEventIdAndTypeAndStatusIn(eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()))
                .thenReturn(0L);
        when(registrationRepository.save(any(Registration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Normal USER attempts to pass a different userId: 999L
        RegistrationRequest request = new RegistrationRequest(999L, 10L, RegistrationType.PARTICIPANT);
        Registration result = registrationService.createRegistration(request);

        // Must register authUser (id 1L), NOT 999L!
        assertEquals(1L, result.getUser().getId());
        assertEquals("test@example.com", result.getUser().getEmail());
        verify(userRepository, never()).findById(999L);
    }

    @Test
    void newRegistrationGetsRegisteredStatusAndTimestampAutomatically() {
        setupSecurityContext("test@example.com", "ROLE_USER");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(authUser));
        when(registrationRepository.existsByUserIdAndEventId(1L, 10L)).thenReturn(false);
        when(registrationRepository.countByEventIdAndTypeAndStatusIn(eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()))
                .thenReturn(0L);

        ArgumentCaptor<Registration> captor = ArgumentCaptor.forClass(Registration.class);
        when(registrationRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationRequest request = new RegistrationRequest(1L, 10L, RegistrationType.PARTICIPANT);
        registrationService.createRegistration(request);

        Registration saved = captor.getValue();
        assertEquals(RegistrationStatus.REGISTERED, saved.getStatus());
        assertNotNull(saved.getRegisteredAt());
        assertTrue(saved.getRegisteredAt().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    // ==========================================
    // STEP 10C: CANCELLATION & CAPACITY RELEASE
    // ==========================================

    @Test
    void cancelRegistration_userCanCancelOwnRegisteredRegistration_success() {
        setupSecurityContext("test@example.com", "ROLE_USER");

        Registration reg = new Registration();
        reg.setId(101L);
        reg.setUser(authUser);
        reg.setEvent(publishedEvent);
        reg.setType(RegistrationType.PARTICIPANT);
        reg.setStatus(RegistrationStatus.REGISTERED);
        reg.setRegisteredAt(LocalDateTime.now().minusDays(1));

        when(registrationRepository.findById(101L)).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any(Registration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Registration cancelled = registrationService.cancelRegistration(101L);

        assertNotNull(cancelled);
        assertEquals(RegistrationStatus.CANCELLED, cancelled.getStatus());
        assertEquals(101L, cancelled.getId());
        assertEquals(authUser, cancelled.getUser());
        assertEquals(publishedEvent, cancelled.getEvent());
        verify(registrationRepository, never()).deleteById(any());
        verify(registrationRepository, never()).delete(any());
    }

    @Test
    void cancelRegistration_userCanCancelOwnAcceptedRegistration_success() {
        setupSecurityContext("test@example.com", "ROLE_USER");

        Registration reg = new Registration();
        reg.setId(102L);
        reg.setUser(authUser);
        reg.setEvent(publishedEvent);
        reg.setType(RegistrationType.PARTICIPANT);
        reg.setStatus(RegistrationStatus.ACCEPTED);

        when(registrationRepository.findById(102L)).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any(Registration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Registration cancelled = registrationService.cancelRegistration(102L);

        assertEquals(RegistrationStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void cancelRegistration_userCannotCancelAnotherUsersRegistration_throwsForbidden() {
        setupSecurityContext("otheruser@example.com", "ROLE_USER");

        Registration reg = new Registration();
        reg.setId(103L);
        reg.setUser(authUser); // authUser is test@example.com
        reg.setEvent(publishedEvent);
        reg.setStatus(RegistrationStatus.REGISTERED);

        when(registrationRepository.findById(103L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.cancelRegistration(103L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Users can only cancel their own registrations"));
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void cancelRegistration_organizerCanCancelRegistrationForOwnEvent_success() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");

        Registration reg = new Registration();
        reg.setId(104L);
        reg.setUser(authUser);
        reg.setEvent(publishedEvent); // organized by organizer@example.com
        reg.setStatus(RegistrationStatus.REGISTERED);

        when(registrationRepository.findById(104L)).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any(Registration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Registration cancelled = registrationService.cancelRegistration(104L);

        assertEquals(RegistrationStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void cancelRegistration_organizerCannotCancelRegistrationForAnotherOrganizersEvent_throwsForbidden() {
        setupSecurityContext("different.organizer@example.com", "ROLE_ORGANIZER");

        Registration reg = new Registration();
        reg.setId(105L);
        reg.setUser(authUser);
        reg.setEvent(publishedEvent); // organized by organizer@example.com
        reg.setStatus(RegistrationStatus.REGISTERED);

        when(registrationRepository.findById(105L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.cancelRegistration(105L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Organizers can only cancel registrations for events they organize"));
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void cancelRegistration_adminCanCancelAnyRegistration_success() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");

        Registration reg = new Registration();
        reg.setId(106L);
        reg.setUser(authUser);
        reg.setEvent(publishedEvent);
        reg.setStatus(RegistrationStatus.REGISTERED);

        when(registrationRepository.findById(106L)).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any(Registration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Registration cancelled = registrationService.cancelRegistration(106L);

        assertEquals(RegistrationStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void cancelRegistration_alreadyCancelled_throwsConflict() {
        setupSecurityContext("test@example.com", "ROLE_USER");

        Registration reg = new Registration();
        reg.setId(107L);
        reg.setUser(authUser);
        reg.setEvent(publishedEvent);
        reg.setStatus(RegistrationStatus.CANCELLED);

        when(registrationRepository.findById(107L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.cancelRegistration(107L)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Registration is already cancelled", ex.getMessage());
    }

    @Test
    void cancelRegistration_rejected_throwsConflict() {
        setupSecurityContext("test@example.com", "ROLE_USER");

        Registration reg = new Registration();
        reg.setId(108L);
        reg.setUser(authUser);
        reg.setEvent(publishedEvent);
        reg.setStatus(RegistrationStatus.REJECTED);

        when(registrationRepository.findById(108L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.cancelRegistration(108L)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Rejected registrations cannot be cancelled", ex.getMessage());
    }

    @Test
    void cancelRegistration_completedEvent_throwsBadRequest() {
        setupSecurityContext("test@example.com", "ROLE_USER");
        publishedEvent.setStatus(EventStatus.COMPLETED);

        Registration reg = new Registration();
        reg.setId(109L);
        reg.setUser(authUser);
        reg.setEvent(publishedEvent);
        reg.setStatus(RegistrationStatus.REGISTERED);

        when(registrationRepository.findById(109L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.cancelRegistration(109L)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Cannot cancel registration for an event that is COMPLETED"));
    }

    @Test
    void cancelRegistration_cancelledEvent_throwsBadRequest() {
        setupSecurityContext("test@example.com", "ROLE_USER");
        publishedEvent.setStatus(EventStatus.CANCELLED);

        Registration reg = new Registration();
        reg.setId(110L);
        reg.setUser(authUser);
        reg.setEvent(publishedEvent);
        reg.setStatus(RegistrationStatus.REGISTERED);

        when(registrationRepository.findById(110L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.cancelRegistration(110L)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Cannot cancel registration for an event that is CANCELLED"));
    }

    @Test
    void cancelRegistration_nonExistentRegistration_throwsNotFound() {
        when(registrationRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                registrationService.cancelRegistration(999L)
        );

        assertTrue(ex.getMessage().contains("Registration not found with id: 999"));
    }

    @Test
    void capacityRelease_participantCancellationReleasesCapacity_newParticipantCanRegister() {
        // Event has capacity 1
        publishedEvent.setCapacity(1);

        // 1. Participant A cancels
        setupSecurityContext("test@example.com", "ROLE_USER");
        Registration regA = new Registration();
        regA.setId(201L);
        regA.setUser(authUser);
        regA.setEvent(publishedEvent);
        regA.setType(RegistrationType.PARTICIPANT);
        regA.setStatus(RegistrationStatus.REGISTERED);

        when(registrationRepository.findById(201L)).thenReturn(Optional.of(regA));
        when(registrationRepository.save(any(Registration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Registration cancelledA = registrationService.cancelRegistration(201L);
        assertEquals(RegistrationStatus.CANCELLED, cancelledA.getStatus());

        // 2. New Participant B registers
        User userB = new User();
        userB.setId(2L);
        userB.setName("User B");
        userB.setEmail("userb@example.com");
        userB.setRole(Role.USER);

        setupSecurityContext("userb@example.com", "ROLE_USER");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(userRepository.findByEmail("userb@example.com")).thenReturn(Optional.of(userB));
        when(registrationRepository.existsByUserIdAndEventId(2L, 10L)).thenReturn(false);

        // Because regA is CANCELLED, count of ACTIVE statuses [REGISTERED, ACCEPTED] is now 0 (< capacity 1)
        when(registrationRepository.countByEventIdAndTypeAndStatusIn(eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()))
                .thenReturn(0L);

        RegistrationRequest reqB = new RegistrationRequest(2L, 10L, RegistrationType.PARTICIPANT);
        Registration resultB = registrationService.createRegistration(reqB);

        assertNotNull(resultB);
        assertEquals(RegistrationStatus.REGISTERED, resultB.getStatus());
        assertEquals(userB, resultB.getUser());
    }

    @Test
    void capacityRelease_volunteerCancellationReleasesCapacity_newVolunteerCanRegister() {
        VolunteerRequirement req = new VolunteerRequirement();
        req.setId(5L);
        req.setEvent(publishedEvent);
        req.setRequiredCount(1);
        when(volunteerRequirementRepository.findByEventId(10L)).thenReturn(Optional.of(req));

        // 1. Volunteer A cancels
        setupSecurityContext("test@example.com", "ROLE_USER");
        Registration regVolA = new Registration();
        regVolA.setId(301L);
        regVolA.setUser(authUser);
        regVolA.setEvent(publishedEvent);
        regVolA.setType(RegistrationType.VOLUNTEER);
        regVolA.setStatus(RegistrationStatus.REGISTERED);

        when(registrationRepository.findById(301L)).thenReturn(Optional.of(regVolA));
        when(registrationRepository.save(any(Registration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Registration cancelledVol = registrationService.cancelRegistration(301L);
        assertEquals(RegistrationStatus.CANCELLED, cancelledVol.getStatus());

        // 2. New Volunteer B registers
        User userB = new User();
        userB.setId(2L);
        userB.setName("User B");
        userB.setEmail("userb@example.com");
        userB.setRole(Role.USER);

        setupSecurityContext("userb@example.com", "ROLE_USER");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(userRepository.findByEmail("userb@example.com")).thenReturn(Optional.of(userB));
        when(registrationRepository.existsByUserIdAndEventId(2L, 10L)).thenReturn(false);

        // Count of ACTIVE volunteers is now 0 (< requiredCount 1)
        when(registrationRepository.countByEventIdAndTypeAndStatusIn(eq(10L), eq(RegistrationType.VOLUNTEER), anyCollection()))
                .thenReturn(0L);

        RegistrationRequest reqVolB = new RegistrationRequest(2L, 10L, RegistrationType.VOLUNTEER);
        Registration resultVolB = registrationService.createRegistration(reqVolB);

        assertNotNull(resultVolB);
        assertEquals(RegistrationStatus.REGISTERED, resultVolB.getStatus());
        assertEquals(RegistrationType.VOLUNTEER, resultVolB.getType());
        assertEquals(userB, resultVolB.getUser());
    }

    // ==========================================
    // STEP 10D: VOLUNTEER MANAGEMENT & ACCEPT/REJECT
    // ==========================================

    private Registration createVolunteerRegistration(Long id, RegistrationStatus status) {
        Registration reg = new Registration();
        reg.setId(id);
        reg.setUser(authUser);
        reg.setEvent(publishedEvent);
        reg.setType(RegistrationType.VOLUNTEER);
        reg.setStatus(status);
        reg.setRegisteredAt(LocalDateTime.now().minusDays(1));
        return reg;
    }

    // --- ACCEPT TESTS (1 - 13) ---

    @Test
    void volunteer_registeredToAccepted_succeeds() {
        Registration reg = createVolunteerRegistration(501L, RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(501L)).thenReturn(Optional.of(reg));
        VolunteerRequirement req = new VolunteerRequirement(publishedEvent, 3, "Volunteers needed");
        when(volunteerRequirementRepository.findByEventId(10L)).thenReturn(Optional.of(req));
        when(registrationRepository.countByEventIdAndTypeAndStatus(10L, RegistrationType.VOLUNTEER, RegistrationStatus.ACCEPTED))
                .thenReturn(1L);
        when(registrationRepository.save(any(Registration.class))).thenAnswer(i -> i.getArgument(0));

        Registration updated = registrationService.updateVolunteerStatus(501L, RegistrationStatus.ACCEPTED);

        assertNotNull(updated);
        assertEquals(RegistrationStatus.ACCEPTED, updated.getStatus());
    }

    @Test
    void organizerCanAcceptVolunteerForOwnEvent_succeeds() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        Registration reg = createVolunteerRegistration(502L, RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(502L)).thenReturn(Optional.of(reg));
        VolunteerRequirement req = new VolunteerRequirement(publishedEvent, 3, "Volunteers");
        when(volunteerRequirementRepository.findByEventId(10L)).thenReturn(Optional.of(req));
        when(registrationRepository.countByEventIdAndTypeAndStatus(10L, RegistrationType.VOLUNTEER, RegistrationStatus.ACCEPTED))
                .thenReturn(0L);
        when(registrationRepository.save(any(Registration.class))).thenAnswer(i -> i.getArgument(0));

        Registration updated = registrationService.updateVolunteerStatus(502L, RegistrationStatus.ACCEPTED);

        assertEquals(RegistrationStatus.ACCEPTED, updated.getStatus());
    }

    @Test
    void adminCanAcceptVolunteerForAnyEvent_succeeds() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");
        Registration reg = createVolunteerRegistration(503L, RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(503L)).thenReturn(Optional.of(reg));
        VolunteerRequirement req = new VolunteerRequirement(publishedEvent, 3, "Volunteers");
        when(volunteerRequirementRepository.findByEventId(10L)).thenReturn(Optional.of(req));
        when(registrationRepository.countByEventIdAndTypeAndStatus(10L, RegistrationType.VOLUNTEER, RegistrationStatus.ACCEPTED))
                .thenReturn(0L);
        when(registrationRepository.save(any(Registration.class))).thenAnswer(i -> i.getArgument(0));

        Registration updated = registrationService.updateVolunteerStatus(503L, RegistrationStatus.ACCEPTED);

        assertEquals(RegistrationStatus.ACCEPTED, updated.getStatus());
    }

    @Test
    void userAttemptingAccept_throwsForbidden() {
        setupSecurityContext("test@example.com", "ROLE_USER");
        Registration reg = createVolunteerRegistration(504L, RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(504L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.updateVolunteerStatus(504L, RegistrationStatus.ACCEPTED)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Users are not authorized to update volunteer status"));
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void organizerAttemptingAcceptOnAnotherOrganizersEvent_throwsForbidden() {
        setupSecurityContext("different.organizer@example.com", "ROLE_ORGANIZER");
        Registration reg = createVolunteerRegistration(505L, RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(505L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.updateVolunteerStatus(505L, RegistrationStatus.ACCEPTED)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Organizers can only update volunteer registrations for events they organize"));
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void acceptingWhenVolunteerCapacityIsFull_throwsConflict() {
        Registration reg = createVolunteerRegistration(506L, RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(506L)).thenReturn(Optional.of(reg));
        VolunteerRequirement req = new VolunteerRequirement(publishedEvent, 2, "Volunteers");
        when(volunteerRequirementRepository.findByEventId(10L)).thenReturn(Optional.of(req));
        when(registrationRepository.countByEventIdAndTypeAndStatus(10L, RegistrationType.VOLUNTEER, RegistrationStatus.ACCEPTED))
                .thenReturn(2L); // Already at capacity 2

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.updateVolunteerStatus(506L, RegistrationStatus.ACCEPTED)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Volunteer capacity has been reached"));
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void participantRegistrationSentToVolunteerStatus_throwsBadRequest() {
        Registration reg = createVolunteerRegistration(507L, RegistrationStatus.REGISTERED);
        reg.setType(RegistrationType.PARTICIPANT);
        when(registrationRepository.findById(507L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.updateVolunteerStatus(507L, RegistrationStatus.ACCEPTED)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Volunteer status can only be changed for volunteer registrations"));
    }

    @Test
    void acceptingAlreadyAcceptedRegistration_throwsConflict() {
        Registration reg = createVolunteerRegistration(508L, RegistrationStatus.ACCEPTED);
        when(registrationRepository.findById(508L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.updateVolunteerStatus(508L, RegistrationStatus.ACCEPTED)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Registration is already accepted", ex.getMessage());
    }

    @Test
    void acceptingRejectedRegistration_throwsConflict() {
        Registration reg = createVolunteerRegistration(509L, RegistrationStatus.REJECTED);
        when(registrationRepository.findById(509L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.updateVolunteerStatus(509L, RegistrationStatus.ACCEPTED)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Registration is already rejected", ex.getMessage());
    }

    @Test
    void acceptingCancelledRegistration_throwsConflict() {
        Registration reg = createVolunteerRegistration(510L, RegistrationStatus.CANCELLED);
        when(registrationRepository.findById(510L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.updateVolunteerStatus(510L, RegistrationStatus.ACCEPTED)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Cancelled registrations cannot be updated", ex.getMessage());
    }

    @Test
    void acceptingForCompletedEvent_throwsBadRequest() {
        publishedEvent.setStatus(EventStatus.COMPLETED);
        Registration reg = createVolunteerRegistration(511L, RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(511L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.updateVolunteerStatus(511L, RegistrationStatus.ACCEPTED)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Volunteer status cannot be updated for an event that is COMPLETED"));
    }

    @Test
    void acceptingForCancelledEvent_throwsBadRequest() {
        publishedEvent.setStatus(EventStatus.CANCELLED);
        Registration reg = createVolunteerRegistration(512L, RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(512L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.updateVolunteerStatus(512L, RegistrationStatus.ACCEPTED)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Volunteer status cannot be updated for an event that is CANCELLED"));
    }

    @Test
    void acceptingWithoutVolunteerRequirement_throwsBadRequest() {
        Registration reg = createVolunteerRegistration(513L, RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(513L)).thenReturn(Optional.of(reg));
        when(volunteerRequirementRepository.findByEventId(10L)).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.updateVolunteerStatus(513L, RegistrationStatus.ACCEPTED)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Volunteer requirement does not exist for this event"));
    }

    // --- REJECT TESTS (14 - 24) ---

    @Test
    void volunteer_registeredToRejected_succeeds() {
        Registration reg = createVolunteerRegistration(514L, RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(514L)).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any(Registration.class))).thenAnswer(i -> i.getArgument(0));

        Registration updated = registrationService.updateVolunteerStatus(514L, RegistrationStatus.REJECTED);

        assertNotNull(updated);
        assertEquals(RegistrationStatus.REJECTED, updated.getStatus());
    }

    @Test
    void organizerCanRejectVolunteerForOwnEvent_succeeds() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        Registration reg = createVolunteerRegistration(515L, RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(515L)).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any(Registration.class))).thenAnswer(i -> i.getArgument(0));

        Registration updated = registrationService.updateVolunteerStatus(515L, RegistrationStatus.REJECTED);

        assertEquals(RegistrationStatus.REJECTED, updated.getStatus());
    }

    @Test
    void adminCanRejectVolunteerForAnyEvent_succeeds() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");
        Registration reg = createVolunteerRegistration(516L, RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(516L)).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any(Registration.class))).thenAnswer(i -> i.getArgument(0));

        Registration updated = registrationService.updateVolunteerStatus(516L, RegistrationStatus.REJECTED);

        assertEquals(RegistrationStatus.REJECTED, updated.getStatus());
    }

    @Test
    void userAttemptingReject_throwsForbidden() {
        setupSecurityContext("test@example.com", "ROLE_USER");
        Registration reg = createVolunteerRegistration(517L, RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(517L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.updateVolunteerStatus(517L, RegistrationStatus.REJECTED)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Users are not authorized to update volunteer status"));
    }

    @Test
    void organizerAttemptingRejectAnotherOrganizersEvent_throwsForbidden() {
        setupSecurityContext("different.organizer@example.com", "ROLE_ORGANIZER");
        Registration reg = createVolunteerRegistration(518L, RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(518L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.updateVolunteerStatus(518L, RegistrationStatus.REJECTED)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Organizers can only update volunteer registrations for events they organize"));
    }

    @Test
    void rejectingAcceptedRegistration_throwsConflict() {
        Registration reg = createVolunteerRegistration(519L, RegistrationStatus.ACCEPTED);
        when(registrationRepository.findById(519L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.updateVolunteerStatus(519L, RegistrationStatus.REJECTED)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Registration is already accepted", ex.getMessage());
    }

    @Test
    void rejectingAlreadyRejectedRegistration_throwsConflict() {
        Registration reg = createVolunteerRegistration(520L, RegistrationStatus.REJECTED);
        when(registrationRepository.findById(520L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.updateVolunteerStatus(520L, RegistrationStatus.REJECTED)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Registration is already rejected", ex.getMessage());
    }

    @Test
    void rejectingCancelledRegistration_throwsConflict() {
        Registration reg = createVolunteerRegistration(521L, RegistrationStatus.CANCELLED);
        when(registrationRepository.findById(521L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.updateVolunteerStatus(521L, RegistrationStatus.REJECTED)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("Cancelled registrations cannot be updated", ex.getMessage());
    }

    @Test
    void rejectingParticipantRegistration_throwsBadRequest() {
        Registration reg = createVolunteerRegistration(522L, RegistrationStatus.REGISTERED);
        reg.setType(RegistrationType.PARTICIPANT);
        when(registrationRepository.findById(522L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.updateVolunteerStatus(522L, RegistrationStatus.REJECTED)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Volunteer status can only be changed for volunteer registrations"));
    }

    @Test
    void rejectingForCompletedEvent_throwsBadRequest() {
        publishedEvent.setStatus(EventStatus.COMPLETED);
        Registration reg = createVolunteerRegistration(523L, RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(523L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.updateVolunteerStatus(523L, RegistrationStatus.REJECTED)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Volunteer status cannot be updated for an event that is COMPLETED"));
    }

    @Test
    void rejectingForCancelledEvent_throwsBadRequest() {
        publishedEvent.setStatus(EventStatus.CANCELLED);
        Registration reg = createVolunteerRegistration(524L, RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(524L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.updateVolunteerStatus(524L, RegistrationStatus.REJECTED)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Volunteer status cannot be updated for an event that is CANCELLED"));
    }

    // --- CAPACITY TESTS (25 - 28) ---

    @Test
    void capacity_rejectedVolunteerDoesNotConsumeCapacity() {
        Registration reg = createVolunteerRegistration(525L, RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(525L)).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any(Registration.class))).thenAnswer(i -> i.getArgument(0));

        Registration rejected = registrationService.updateVolunteerStatus(525L, RegistrationStatus.REJECTED);
        assertEquals(RegistrationStatus.REJECTED, rejected.getStatus());

        // Acceptance capacity check queries countByEventIdAndTypeAndStatus with ACCEPTED status
        verify(registrationRepository, never()).countByEventIdAndTypeAndStatus(anyLong(), any(), eq(RegistrationStatus.ACCEPTED));
    }

    @Test
    void capacity_acceptedVolunteerConsumesCapacity() {
        Registration reg1 = createVolunteerRegistration(526L, RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(526L)).thenReturn(Optional.of(reg1));
        VolunteerRequirement req = new VolunteerRequirement(publishedEvent, 1, "1 volunteer required");
        when(volunteerRequirementRepository.findByEventId(10L)).thenReturn(Optional.of(req));
        // First volunteer accepted: count is 0 < 1
        when(registrationRepository.countByEventIdAndTypeAndStatus(10L, RegistrationType.VOLUNTEER, RegistrationStatus.ACCEPTED))
                .thenReturn(0L);
        when(registrationRepository.save(any(Registration.class))).thenAnswer(i -> i.getArgument(0));

        Registration accepted1 = registrationService.updateVolunteerStatus(526L, RegistrationStatus.ACCEPTED);
        assertEquals(RegistrationStatus.ACCEPTED, accepted1.getStatus());

        // Next accept attempt when count is 1 == requiredCount 1:
        Registration reg2 = createVolunteerRegistration(527L, RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(527L)).thenReturn(Optional.of(reg2));
        when(registrationRepository.countByEventIdAndTypeAndStatus(10L, RegistrationType.VOLUNTEER, RegistrationStatus.ACCEPTED))
                .thenReturn(1L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.updateVolunteerStatus(527L, RegistrationStatus.ACCEPTED)
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Volunteer capacity has been reached"));
    }

    @Test
    void capacity_cancelledAcceptedVolunteerReleasesCapacity() {
        setupSecurityContext("test@example.com", "ROLE_USER");
        Registration reg = createVolunteerRegistration(528L, RegistrationStatus.ACCEPTED);
        when(registrationRepository.findById(528L)).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any(Registration.class))).thenAnswer(i -> i.getArgument(0));

        Registration cancelled = registrationService.cancelRegistration(528L);
        assertEquals(RegistrationStatus.CANCELLED, cancelled.getStatus());
        // Since status is CANCELLED, it does not match RegistrationStatus.ACCEPTED
        assertNotEquals(RegistrationStatus.ACCEPTED, cancelled.getStatus());
    }

    @Test
    void capacity_afterVolunteerSlotReleased_anotherRegisteredVolunteerCanBeAccepted() {
        // Event requirement = 1
        VolunteerRequirement req = new VolunteerRequirement(publishedEvent, 1, "1 volunteer needed");
        when(volunteerRequirementRepository.findByEventId(10L)).thenReturn(Optional.of(req));

        // After previous volunteer cancelled, count of ACCEPTED is 0
        when(registrationRepository.countByEventIdAndTypeAndStatus(10L, RegistrationType.VOLUNTEER, RegistrationStatus.ACCEPTED))
                .thenReturn(0L);

        Registration reg2 = createVolunteerRegistration(529L, RegistrationStatus.REGISTERED);
        when(registrationRepository.findById(529L)).thenReturn(Optional.of(reg2));
        when(registrationRepository.save(any(Registration.class))).thenAnswer(i -> i.getArgument(0));

        Registration accepted2 = registrationService.updateVolunteerStatus(529L, RegistrationStatus.ACCEPTED);
        assertNotNull(accepted2);
        assertEquals(RegistrationStatus.ACCEPTED, accepted2.getStatus());
    }

    // ==========================================
    // SECURITY HARDENING TESTS (PHASE 12B)
    // ==========================================

    @Test
    void getAllRegistrations_adminCanAccessAllRegistrations_success() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");
        Registration reg = new Registration();
        reg.setId(100L);
        when(registrationRepository.findAll()).thenReturn(List.of(reg));

        List<Registration> result = registrationService.getAllRegistrations();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(registrationRepository).findAll();
    }

    @Test
    void getAllRegistrations_userCannotAccessAllRegistrations_throwsForbidden() {
        setupSecurityContext("test@example.com", "ROLE_USER");

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.getAllRegistrations()
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Only administrators can retrieve all registrations"));
        verify(registrationRepository, never()).findAll();
    }

    @Test
    void getAllRegistrations_organizerCannotAccessAllRegistrations_throwsForbidden() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.getAllRegistrations()
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Only administrators can retrieve all registrations"));
        verify(registrationRepository, never()).findAll();
    }

    @Test
    void getRegistrationsByUser_userCanAccessOwnRegistrationHistory_success() {
        setupSecurityContext("test@example.com", "ROLE_USER");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(authUser));
        when(userRepository.existsById(1L)).thenReturn(true);

        Registration reg = new Registration();
        reg.setId(201L);
        reg.setUser(authUser);
        when(registrationRepository.findByUserId(1L)).thenReturn(List.of(reg));

        List<Registration> result = registrationService.getRegistrationsByUser(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(registrationRepository).findByUserId(1L);
    }

    @Test
    void getRegistrationsByUser_userCannotAccessAnotherUsersRegistrationHistory_throwsForbidden() {
        setupSecurityContext("test@example.com", "ROLE_USER");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(authUser));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.getRegistrationsByUser(2L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Users can only view their own registrations"));
        verify(registrationRepository, never()).findByUserId(any());
    }

    @Test
    void getRegistrationsByUser_organizerCannotAccessAnotherUsersRegistrationHistory_throwsForbidden() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        User organizer = publishedEvent.getOrganizer();
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizer));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.getRegistrationsByUser(1L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Users can only view their own registrations"));
        verify(registrationRepository, never()).findByUserId(any());
    }

    @Test
    void getRegistrationsByUser_adminCanAccessAnyUsersRegistrationHistory_success() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");
        when(userRepository.existsById(1L)).thenReturn(true);

        Registration reg = new Registration();
        reg.setId(202L);
        when(registrationRepository.findByUserId(1L)).thenReturn(List.of(reg));

        List<Registration> result = registrationService.getRegistrationsByUser(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(registrationRepository).findByUserId(1L);
    }

    @Test
    void getRegistrationsByUser_adminAccessingNonexistentUser_throwsNotFound() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");
        when(userRepository.existsById(999L)).thenReturn(false);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                registrationService.getRegistrationsByUser(999L)
        );

        assertTrue(ex.getMessage().contains("User not found with id: 999"));
        verify(registrationRepository, never()).findByUserId(any());
    }

    @Test
    void getRegistrationsByEvent_eventOwnerCanAccessAttendeeList_success() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));

        Registration reg = new Registration();
        reg.setId(301L);
        reg.setEvent(publishedEvent);
        when(registrationRepository.findByEventId(10L)).thenReturn(List.of(reg));

        List<Registration> result = registrationService.getRegistrationsByEvent(10L);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(registrationRepository).findByEventId(10L);
    }

    @Test
    void getRegistrationsByEvent_unrelatedUserCannotAccessAttendeeList_throwsForbidden() {
        setupSecurityContext("test@example.com", "ROLE_USER");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.getRegistrationsByEvent(10L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Users are not authorized to view event attendee lists"));
        verify(registrationRepository, never()).findByEventId(any());
    }

    @Test
    void getRegistrationsByEvent_nonOwningOrganizerCannotAccessAttendeeList_throwsForbidden() {
        setupSecurityContext("other.organizer@example.com", "ROLE_ORGANIZER");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.getRegistrationsByEvent(10L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Organizers can only view attendee lists for their own events"));
        verify(registrationRepository, never()).findByEventId(any());
    }

    @Test
    void getRegistrationsByEvent_adminCanAccessAnyEventAttendeeList_success() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));

        Registration reg = new Registration();
        reg.setId(302L);
        when(registrationRepository.findByEventId(10L)).thenReturn(List.of(reg));

        List<Registration> result = registrationService.getRegistrationsByEvent(10L);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(registrationRepository).findByEventId(10L);
    }

    @Test
    void getRegistrationsByEvent_nonexistentEvent_throwsNotFound() {
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                registrationService.getRegistrationsByEvent(999L)
        );

        assertTrue(ex.getMessage().contains("Event not found with id: 999"));
        verify(registrationRepository, never()).findByEventId(any());
    }

    @Test
    void getRegistrationsByEvent_filterByTypeAndStatus_callsSpecificRepoMethod() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));

        Registration reg = new Registration();
        reg.setId(303L);
        when(registrationRepository.findByEventIdAndTypeAndStatus(10L, RegistrationType.PARTICIPANT, RegistrationStatus.REGISTERED))
                .thenReturn(List.of(reg));

        List<Registration> result = registrationService.getRegistrationsByEvent(10L, RegistrationType.PARTICIPANT, RegistrationStatus.REGISTERED);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(registrationRepository).findByEventIdAndTypeAndStatus(10L, RegistrationType.PARTICIPANT, RegistrationStatus.REGISTERED);
    }

    @Test
    void getRegistrationsByEvent_filterByTypeOnly_callsTypeRepoMethod() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));

        Registration reg = new Registration();
        reg.setId(304L);
        when(registrationRepository.findByEventIdAndType(10L, RegistrationType.VOLUNTEER))
                .thenReturn(List.of(reg));

        List<Registration> result = registrationService.getRegistrationsByEvent(10L, RegistrationType.VOLUNTEER, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(registrationRepository).findByEventIdAndType(10L, RegistrationType.VOLUNTEER);
    }

    @Test
    void getRegistrationsByEvent_filterByStatusOnly_callsStatusRepoMethod() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));

        Registration reg = new Registration();
        reg.setId(305L);
        when(registrationRepository.findByEventIdAndStatus(10L, RegistrationStatus.ACCEPTED))
                .thenReturn(List.of(reg));

        List<Registration> result = registrationService.getRegistrationsByEvent(10L, null, RegistrationStatus.ACCEPTED);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(registrationRepository).findByEventIdAndStatus(10L, RegistrationStatus.ACCEPTED);
    }

    @Test
    void getRegistrationSummaryForEvent_owningOrganizer_returnsAccurateSummary() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));

        when(registrationRepository.countByEventId(10L)).thenReturn(15L);
        when(registrationRepository.countByEventIdAndType(10L, RegistrationType.PARTICIPANT)).thenReturn(10L);
        when(registrationRepository.countByEventIdAndType(10L, RegistrationType.VOLUNTEER)).thenReturn(5L);

        when(registrationRepository.countByEventIdAndStatus(10L, RegistrationStatus.REGISTERED)).thenReturn(8L);
        when(registrationRepository.countByEventIdAndStatus(10L, RegistrationStatus.ACCEPTED)).thenReturn(4L);
        when(registrationRepository.countByEventIdAndStatus(10L, RegistrationStatus.CANCELLED)).thenReturn(2L);
        when(registrationRepository.countByEventIdAndStatus(10L, RegistrationStatus.REJECTED)).thenReturn(1L);

        EventRegistrationSummaryResponse summary = registrationService.getRegistrationSummaryForEvent(10L);

        assertNotNull(summary);
        assertEquals(10L, summary.getEventId());
        assertEquals(15L, summary.getTotalRegistrations());
        assertEquals(10L, summary.getParticipantCount());
        assertEquals(5L, summary.getVolunteerCount());
        assertEquals(8L, summary.getRegisteredCount());
        assertEquals(4L, summary.getAcceptedCount());
        assertEquals(2L, summary.getCancelledCount());
        assertEquals(1L, summary.getRejectedCount());
        assertEquals(8L, summary.getStatusCounts().get("REGISTERED"));
        assertEquals(4L, summary.getStatusCounts().get("ACCEPTED"));
        assertEquals(2L, summary.getStatusCounts().get("CANCELLED"));
        assertEquals(1L, summary.getStatusCounts().get("REJECTED"));
    }

    @Test
    void getRegistrationSummaryForEvent_emptyResults_returnsZeros() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));

        when(registrationRepository.countByEventId(10L)).thenReturn(0L);
        when(registrationRepository.countByEventIdAndType(10L, RegistrationType.PARTICIPANT)).thenReturn(0L);
        when(registrationRepository.countByEventIdAndType(10L, RegistrationType.VOLUNTEER)).thenReturn(0L);
        when(registrationRepository.countByEventIdAndStatus(10L, RegistrationStatus.REGISTERED)).thenReturn(0L);
        when(registrationRepository.countByEventIdAndStatus(10L, RegistrationStatus.ACCEPTED)).thenReturn(0L);
        when(registrationRepository.countByEventIdAndStatus(10L, RegistrationStatus.CANCELLED)).thenReturn(0L);
        when(registrationRepository.countByEventIdAndStatus(10L, RegistrationStatus.REJECTED)).thenReturn(0L);

        EventRegistrationSummaryResponse summary = registrationService.getRegistrationSummaryForEvent(10L);

        assertNotNull(summary);
        assertEquals(10L, summary.getEventId());
        assertEquals(0L, summary.getTotalRegistrations());
        assertEquals(0L, summary.getParticipantCount());
        assertEquals(0L, summary.getVolunteerCount());
        assertEquals(0L, summary.getRegisteredCount());
        assertEquals(0L, summary.getStatusCounts().get("REGISTERED"));
    }

    @Test
    void getRegistrationSummaryForEvent_adminCanAccessAnyEvent_success() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));

        when(registrationRepository.countByEventId(10L)).thenReturn(5L);
        when(registrationRepository.countByEventIdAndType(10L, RegistrationType.PARTICIPANT)).thenReturn(5L);
        when(registrationRepository.countByEventIdAndType(10L, RegistrationType.VOLUNTEER)).thenReturn(0L);

        EventRegistrationSummaryResponse summary = registrationService.getRegistrationSummaryForEvent(10L);

        assertNotNull(summary);
        assertEquals(5L, summary.getTotalRegistrations());
    }

    @Test
    void getRegistrationSummaryForEvent_nonOwningOrganizer_throwsForbidden() {
        setupSecurityContext("other.organizer@example.com", "ROLE_ORGANIZER");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.getRegistrationSummaryForEvent(10L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Organizers can only view registration summaries for their own events"));
        verify(registrationRepository, never()).countByEventId(any());
    }

    @Test
    void getRegistrationSummaryForEvent_normalUser_throwsForbidden() {
        setupSecurityContext("user@example.com", "ROLE_USER");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.getRegistrationSummaryForEvent(10L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Users are not authorized to view event registration summaries"));
        verify(registrationRepository, never()).countByEventId(any());
    }

    @Test
    void getRegistrationSummaryForEvent_nonexistentEvent_throwsNotFound() {
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                registrationService.getRegistrationSummaryForEvent(999L)
        );

        assertTrue(ex.getMessage().contains("Event not found with id: 999"));
        verify(registrationRepository, never()).countByEventId(any());
    }

    @Test
    void getRegistrationById_userCanAccessOwnRegistration_success() {
        setupSecurityContext("test@example.com", "ROLE_USER");
        Registration reg = new Registration();
        reg.setId(401L);
        reg.setUser(authUser);
        reg.setEvent(publishedEvent);
        when(registrationRepository.findById(401L)).thenReturn(Optional.of(reg));

        Registration result = registrationService.getRegistrationById(401L);

        assertNotNull(result);
        assertEquals(401L, result.getId());
    }

    @Test
    void getRegistrationById_eventOwnerCanAccessRegistration_success() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        Registration reg = new Registration();
        reg.setId(402L);
        reg.setUser(authUser);
        reg.setEvent(publishedEvent);
        when(registrationRepository.findById(402L)).thenReturn(Optional.of(reg));

        Registration result = registrationService.getRegistrationById(402L);

        assertNotNull(result);
        assertEquals(402L, result.getId());
    }

    @Test
    void getRegistrationById_adminCanAccessAnyRegistration_success() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");
        Registration reg = new Registration();
        reg.setId(403L);
        reg.setUser(authUser);
        reg.setEvent(publishedEvent);
        when(registrationRepository.findById(403L)).thenReturn(Optional.of(reg));

        Registration result = registrationService.getRegistrationById(403L);

        assertNotNull(result);
        assertEquals(403L, result.getId());
    }

    @Test
    void getRegistrationById_unrelatedUserCannotAccessRegistration_throwsForbidden() {
        setupSecurityContext("stranger@example.com", "ROLE_USER");
        Registration reg = new Registration();
        reg.setId(404L);
        reg.setUser(authUser);
        reg.setEvent(publishedEvent);
        when(registrationRepository.findById(404L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.getRegistrationById(404L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("You are not authorized to view this registration"));
    }

    @Test
    void getRegistrationById_nonexistentRegistration_throwsNotFound() {
        when(registrationRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                registrationService.getRegistrationById(999L)
        );

        assertTrue(ex.getMessage().contains("Registration not found with id: 999"));
    }

    @Test
    void deleteRegistration_eventOwnerCanDeleteRegistration_success() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        Registration reg = new Registration();
        reg.setId(501L);
        reg.setEvent(publishedEvent);
        when(registrationRepository.findById(501L)).thenReturn(Optional.of(reg));

        registrationService.deleteRegistration(501L);

        verify(registrationRepository).delete(reg);
    }

    @Test
    void deleteRegistration_adminCanDeleteAnyRegistration_success() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");
        Registration reg = new Registration();
        reg.setId(502L);
        reg.setEvent(publishedEvent);
        when(registrationRepository.findById(502L)).thenReturn(Optional.of(reg));

        registrationService.deleteRegistration(502L);

        verify(registrationRepository).delete(reg);
    }

    @Test
    void deleteRegistration_nonOwningOrganizerCannotDeleteRegistration_throwsForbidden() {
        setupSecurityContext("other.organizer@example.com", "ROLE_ORGANIZER");
        Registration reg = new Registration();
        reg.setId(503L);
        reg.setEvent(publishedEvent);
        when(registrationRepository.findById(503L)).thenReturn(Optional.of(reg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                registrationService.deleteRegistration(503L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Organizers can only delete registrations for events they organize"));
        verify(registrationRepository, never()).delete(any());
    }
}
