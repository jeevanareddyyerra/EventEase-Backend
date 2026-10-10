package com.eventease.service;

import com.eventease.dto.request.TeamRequest;
import com.eventease.entity.*;
import com.eventease.exception.BusinessRuleException;
import com.eventease.exception.ResourceNotFoundException;
import com.eventease.repository.EventRepository;
import com.eventease.repository.RegistrationRepository;
import com.eventease.repository.TeamMemberRepository;
import com.eventease.repository.TeamRepository;
import com.eventease.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RegistrationRepository registrationRepository;

    @InjectMocks
    private TeamService teamService;

    private User authUser;
    private User organizerUser;
    private User adminUser;
    private User participantUser;
    private Event publishedEvent;

    @BeforeEach
    void setUp() {
        authUser = new User();
        authUser.setId(1L);
        authUser.setName("Test User");
        authUser.setEmail("user@example.com");
        authUser.setRole(Role.USER);

        organizerUser = new User();
        organizerUser.setId(2L);
        organizerUser.setName("Organizer User");
        organizerUser.setEmail("organizer@example.com");
        organizerUser.setRole(Role.ORGANIZER);

        adminUser = new User();
        adminUser.setId(3L);
        adminUser.setName("Admin User");
        adminUser.setEmail("admin@example.com");
        adminUser.setRole(Role.ADMIN);

        participantUser = new User();
        participantUser.setId(4L);
        participantUser.setName("Participant User");
        participantUser.setEmail("participant@example.com");
        participantUser.setRole(Role.USER);

        publishedEvent = new Event();
        publishedEvent.setId(10L);
        publishedEvent.setTitle("Hackathon 2026");
        publishedEvent.setStatus(EventStatus.PUBLISHED);
        publishedEvent.setCapacity(50);
        publishedEvent.setOrganizer(organizerUser);
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

    // 1. USER creates team for own event participation -> success
    @Test
    void userCreatesTeamForOwnEventParticipation_success() {
        setupSecurityContext("user@example.com", "ROLE_USER");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(authUser));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(teamRepository.existsByEventIdAndName(10L, "Code Warriors")).thenReturn(false);
        when(registrationRepository.existsByUserIdAndEventIdAndTypeAndStatusIn(
                eq(1L), eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()
        )).thenReturn(true);
        when(teamRepository.existsByEventIdAndLeaderId(10L, 1L)).thenReturn(false);
        when(teamMemberRepository.existsByTeamEventIdAndUserId(10L, 1L)).thenReturn(false);
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> {
            Team t = inv.getArgument(0);
            t.setId(100L);
            return t;
        });

        TeamRequest request = new TeamRequest("Code Warriors", 10L, 1L);
        Team created = teamService.createTeam(request);

        assertNotNull(created);
        assertEquals("Code Warriors", created.getName());
        assertEquals(authUser, created.getLeader());
        assertEquals(publishedEvent, created.getEvent());
        verify(teamRepository).save(any(Team.class));
    }

    // 2. USER attempts spoofed leaderId -> rejected/ignored and authenticated user remains leader
    @Test
    void userAttemptsSpoofedLeaderId_ignoredAndAuthUserRemainsLeader() {
        setupSecurityContext("user@example.com", "ROLE_USER");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(authUser));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(teamRepository.existsByEventIdAndName(10L, "Spoofers")).thenReturn(false);
        when(registrationRepository.existsByUserIdAndEventIdAndTypeAndStatusIn(
                eq(1L), eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()
        )).thenReturn(true);
        when(teamRepository.existsByEventIdAndLeaderId(10L, 1L)).thenReturn(false);
        when(teamMemberRepository.existsByTeamEventIdAndUserId(10L, 1L)).thenReturn(false);
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        // USER provides spoofed leaderId = 999L
        TeamRequest request = new TeamRequest("Spoofers", 10L, 999L);
        Team created = teamService.createTeam(request);

        assertNotNull(created);
        assertEquals(authUser, created.getLeader());
        assertEquals(1L, created.getLeader().getId());
    }

    // 3. USER creates team without participant registration -> 400
    @Test
    void userCreatesTeamWithoutParticipantRegistration_throws400() {
        setupSecurityContext("user@example.com", "ROLE_USER");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(authUser));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(teamRepository.existsByEventIdAndName(10L, "No Reg")).thenReturn(false);
        when(registrationRepository.existsByUserIdAndEventIdAndTypeAndStatusIn(
                eq(1L), eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()
        )).thenReturn(false);

        TeamRequest request = new TeamRequest("No Reg", 10L, 1L);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamService.createTeam(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("active participant registration"));
    }

    // 4. USER has only VOLUNTEER registration -> 400
    @Test
    void userHasOnlyVolunteerRegistration_throws400() {
        setupSecurityContext("user@example.com", "ROLE_USER");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(authUser));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(teamRepository.existsByEventIdAndName(10L, "Volunteer Team")).thenReturn(false);
        // Returns false because registration is not of type PARTICIPANT
        when(registrationRepository.existsByUserIdAndEventIdAndTypeAndStatusIn(
                eq(1L), eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()
        )).thenReturn(false);

        TeamRequest request = new TeamRequest("Volunteer Team", 10L, 1L);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamService.createTeam(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    // 5. USER has CANCELLED participant registration -> 400
    @Test
    void userHasCancelledParticipantRegistration_throws400() {
        setupSecurityContext("user@example.com", "ROLE_USER");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(authUser));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(teamRepository.existsByEventIdAndName(10L, "Cancelled Team")).thenReturn(false);
        // Returns false because status is CANCELLED (not REGISTERED or ACCEPTED)
        when(registrationRepository.existsByUserIdAndEventIdAndTypeAndStatusIn(
                eq(1L), eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()
        )).thenReturn(false);

        TeamRequest request = new TeamRequest("Cancelled Team", 10L, 1L);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamService.createTeam(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    // 6. ORGANIZER creates team for own event -> success
    @Test
    void organizerCreatesTeamForOwnEvent_success() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerUser));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(userRepository.findById(4L)).thenReturn(Optional.of(participantUser));
        when(teamRepository.existsByEventIdAndName(10L, "Org Team")).thenReturn(false);
        when(registrationRepository.existsByUserIdAndEventIdAndTypeAndStatusIn(
                eq(4L), eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()
        )).thenReturn(true);
        when(teamRepository.existsByEventIdAndLeaderId(10L, 4L)).thenReturn(false);
        when(teamMemberRepository.existsByTeamEventIdAndUserId(10L, 4L)).thenReturn(false);
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        TeamRequest request = new TeamRequest("Org Team", 10L, 4L);
        Team created = teamService.createTeam(request);

        assertNotNull(created);
        assertEquals(participantUser, created.getLeader());
    }

    // 7. ORGANIZER creates team for another organizer's event -> 403
    @Test
    void organizerCreatesTeamForAnotherOrganizersEvent_throws403() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerUser));

        User otherOrganizer = new User();
        otherOrganizer.setId(99L);
        otherOrganizer.setEmail("other@example.com");

        Event otherEvent = new Event();
        otherEvent.setId(20L);
        otherEvent.setStatus(EventStatus.PUBLISHED);
        otherEvent.setOrganizer(otherOrganizer);

        when(eventRepository.findById(20L)).thenReturn(Optional.of(otherEvent));

        TeamRequest request = new TeamRequest("Intruder", 20L, 4L);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamService.createTeam(request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    // 8. ADMIN creates team for any event -> success
    @Test
    void adminCreatesTeamForAnyEvent_success() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(userRepository.findById(4L)).thenReturn(Optional.of(participantUser));
        when(teamRepository.existsByEventIdAndName(10L, "Admin Team")).thenReturn(false);
        when(registrationRepository.existsByUserIdAndEventIdAndTypeAndStatusIn(
                eq(4L), eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()
        )).thenReturn(true);
        when(teamRepository.existsByEventIdAndLeaderId(10L, 4L)).thenReturn(false);
        when(teamMemberRepository.existsByTeamEventIdAndUserId(10L, 4L)).thenReturn(false);
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        TeamRequest request = new TeamRequest("Admin Team", 10L, 4L);
        Team created = teamService.createTeam(request);

        assertNotNull(created);
        assertEquals(participantUser, created.getLeader());
    }

    // 9. duplicate team name within same event -> 409
    @Test
    void duplicateTeamNameWithinSameEvent_throws409() {
        setupSecurityContext("user@example.com", "ROLE_USER");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(authUser));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(teamRepository.existsByEventIdAndName(10L, "Alpha")).thenReturn(true);

        TeamRequest request = new TeamRequest("Alpha", 10L, 1L);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamService.createTeam(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("already exists"));
    }

    // 10. same team name in different event -> allowed
    @Test
    void sameTeamNameInDifferentEvent_allowed() {
        Event event2 = new Event();
        event2.setId(20L);
        event2.setStatus(EventStatus.PUBLISHED);

        setupSecurityContext("user@example.com", "ROLE_USER");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(authUser));
        when(eventRepository.findById(20L)).thenReturn(Optional.of(event2));
        when(teamRepository.existsByEventIdAndName(20L, "Alpha")).thenReturn(false);
        when(registrationRepository.existsByUserIdAndEventIdAndTypeAndStatusIn(
                eq(1L), eq(20L), eq(RegistrationType.PARTICIPANT), anyCollection()
        )).thenReturn(true);
        when(teamRepository.existsByEventIdAndLeaderId(20L, 1L)).thenReturn(false);
        when(teamMemberRepository.existsByTeamEventIdAndUserId(20L, 1L)).thenReturn(false);
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        TeamRequest request = new TeamRequest("Alpha", 20L, 1L);
        Team created = teamService.createTeam(request);

        assertNotNull(created);
        assertEquals("Alpha", created.getName());
    }

    // 11. team for COMPLETED event -> 400
    @Test
    void teamForCompletedEvent_throws400() {
        Event completed = new Event();
        completed.setId(11L);
        completed.setStatus(EventStatus.COMPLETED);

        when(eventRepository.findById(11L)).thenReturn(Optional.of(completed));

        TeamRequest request = new TeamRequest("Done", 11L, 1L);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamService.createTeam(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("COMPLETED"));
    }

    // 12. team for CANCELLED event -> 400
    @Test
    void teamForCancelledEvent_throws400() {
        Event cancelled = new Event();
        cancelled.setId(12L);
        cancelled.setStatus(EventStatus.CANCELLED);

        when(eventRepository.findById(12L)).thenReturn(Optional.of(cancelled));

        TeamRequest request = new TeamRequest("Cancelled", 12L, 1L);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamService.createTeam(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("CANCELLED"));
    }

    // 13. missing event -> 404
    @Test
    void missingEvent_throws404() {
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());

        TeamRequest request = new TeamRequest("Missing", 999L, 1L);
        assertThrows(ResourceNotFoundException.class, () -> teamService.createTeam(request));
    }

    // 14. missing leader -> 404
    @Test
    void missingLeader_throws404() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        TeamRequest request = new TeamRequest("No Leader", 10L, 999L);
        assertThrows(ResourceNotFoundException.class, () -> teamService.createTeam(request));
    }

    // Leader already leading another team in same event -> 409
    @Test
    void leaderAlreadyLeadingAnotherTeamInSameEvent_throws409() {
        setupSecurityContext("user@example.com", "ROLE_USER");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(authUser));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(teamRepository.existsByEventIdAndName(10L, "New Team")).thenReturn(false);
        when(registrationRepository.existsByUserIdAndEventIdAndTypeAndStatusIn(
                eq(1L), eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()
        )).thenReturn(true);
        when(teamRepository.existsByEventIdAndLeaderId(10L, 1L)).thenReturn(true);

        TeamRequest request = new TeamRequest("New Team", 10L, 1L);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamService.createTeam(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    // Leader already member of another team in same event -> 409
    @Test
    void leaderAlreadyMemberOfAnotherTeamInSameEvent_throws409() {
        setupSecurityContext("user@example.com", "ROLE_USER");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(authUser));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(publishedEvent));
        when(teamRepository.existsByEventIdAndName(10L, "New Team 2")).thenReturn(false);
        when(registrationRepository.existsByUserIdAndEventIdAndTypeAndStatusIn(
                eq(1L), eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()
        )).thenReturn(true);
        when(teamRepository.existsByEventIdAndLeaderId(10L, 1L)).thenReturn(false);
        when(teamMemberRepository.existsByTeamEventIdAndUserId(10L, 1L)).thenReturn(true);

        TeamRequest request = new TeamRequest("New Team 2", 10L, 1L);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamService.createTeam(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    // 28. USER leader deletes own team -> success
    @Test
    void userLeaderDeletesOwnTeam_success() {
        setupSecurityContext("user@example.com", "ROLE_USER");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(authUser));

        Team team = new Team("My Team", publishedEvent, authUser);
        team.setId(50L);
        when(teamRepository.findById(50L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamId(50L)).thenReturn(Collections.emptyList());

        teamService.deleteTeam(50L);

        verify(teamMemberRepository).deleteAll(anyList());
        verify(teamRepository).delete(team);
    }

    // 29. USER non-leader deletes team -> 403
    @Test
    void userNonLeaderDeletesTeam_throws403() {
        setupSecurityContext("user@example.com", "ROLE_USER");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(authUser));

        Team team = new Team("Other Team", publishedEvent, participantUser);
        team.setId(50L);
        when(teamRepository.findById(50L)).thenReturn(Optional.of(team));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamService.deleteTeam(50L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    // 30. ORGANIZER deletes team for own event -> success
    @Test
    void organizerDeletesTeamForOwnEvent_success() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerUser));

        Team team = new Team("Participant Team", publishedEvent, participantUser);
        team.setId(50L);
        when(teamRepository.findById(50L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamId(50L)).thenReturn(Collections.emptyList());

        teamService.deleteTeam(50L);

        verify(teamMemberRepository).deleteAll(anyList());
        verify(teamRepository).delete(team);
    }

    // 31. ORGANIZER deletes another organizer's team -> 403
    @Test
    void organizerDeletesAnotherOrganizersTeam_throws403() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerUser));

        User otherOrg = new User();
        otherOrg.setId(99L);
        otherOrg.setEmail("other@example.com");

        Event otherEvent = new Event();
        otherEvent.setId(30L);
        otherEvent.setStatus(EventStatus.PUBLISHED);
        otherEvent.setOrganizer(otherOrg);

        Team team = new Team("Foreign Team", otherEvent, participantUser);
        team.setId(50L);
        when(teamRepository.findById(50L)).thenReturn(Optional.of(team));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamService.deleteTeam(50L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    // 32. ADMIN deletes any team -> success
    @Test
    void adminDeletesAnyTeam_success() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");

        Team team = new Team("Any Team", publishedEvent, participantUser);
        team.setId(50L);
        when(teamRepository.findById(50L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamId(50L)).thenReturn(Collections.emptyList());

        teamService.deleteTeam(50L);

        verify(teamMemberRepository).deleteAll(anyList());
        verify(teamRepository).delete(team);
    }

    // 33. deleting team for COMPLETED event -> 400
    @Test
    void deletingTeamForCompletedEvent_throws400() {
        Event completed = new Event();
        completed.setId(11L);
        completed.setStatus(EventStatus.COMPLETED);

        Team team = new Team("Completed Team", completed, authUser);
        team.setId(50L);
        when(teamRepository.findById(50L)).thenReturn(Optional.of(team));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamService.deleteTeam(50L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("COMPLETED"));
    }

    // 34. deleting team for CANCELLED event -> 400
    @Test
    void deletingTeamForCancelledEvent_throws400() {
        Event cancelled = new Event();
        cancelled.setId(12L);
        cancelled.setStatus(EventStatus.CANCELLED);

        Team team = new Team("Cancelled Team", cancelled, authUser);
        team.setId(50L);
        when(teamRepository.findById(50L)).thenReturn(Optional.of(team));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamService.deleteTeam(50L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("CANCELLED"));
    }

    // 35. team members are safely removed with team
    @Test
    void teamMembersAreSafelyRemovedWithTeam() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");

        Team team = new Team("Team With Members", publishedEvent, authUser);
        team.setId(50L);

        TeamMember member1 = new TeamMember(team, participantUser);
        member1.setId(101L);
        List<TeamMember> members = List.of(member1);

        when(teamRepository.findById(50L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamId(50L)).thenReturn(members);

        teamService.deleteTeam(50L);

        verify(teamMemberRepository).deleteAll(members);
        verify(teamRepository).delete(team);
        verifyNoInteractions(registrationRepository);
    }
}
