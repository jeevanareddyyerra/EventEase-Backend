package com.eventease.service;

import com.eventease.dto.request.TeamMemberRequest;
import com.eventease.entity.*;
import com.eventease.exception.BusinessRuleException;
import com.eventease.exception.ResourceNotFoundException;
import com.eventease.repository.RegistrationRepository;
import com.eventease.repository.TeamMemberRepository;
import com.eventease.repository.TeamRepository;
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
class TeamMemberServiceTest {

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RegistrationRepository registrationRepository;

    @InjectMocks
    private TeamMemberService teamMemberService;

    private User leaderUser;
    private User candidateUser;
    private User otherUser;
    private User organizerUser;
    private User adminUser;
    private Event publishedEvent;
    private Team team;

    @BeforeEach
    void setUp() {
        leaderUser = new User();
        leaderUser.setId(1L);
        leaderUser.setName("Leader User");
        leaderUser.setEmail("leader@example.com");
        leaderUser.setRole(Role.USER);

        candidateUser = new User();
        candidateUser.setId(2L);
        candidateUser.setName("Candidate User");
        candidateUser.setEmail("candidate@example.com");
        candidateUser.setRole(Role.USER);

        otherUser = new User();
        otherUser.setId(3L);
        otherUser.setName("Other User");
        otherUser.setEmail("other@example.com");
        otherUser.setRole(Role.USER);

        organizerUser = new User();
        organizerUser.setId(4L);
        organizerUser.setName("Organizer User");
        organizerUser.setEmail("organizer@example.com");
        organizerUser.setRole(Role.ORGANIZER);

        adminUser = new User();
        adminUser.setId(5L);
        adminUser.setName("Admin User");
        adminUser.setEmail("admin@example.com");
        adminUser.setRole(Role.ADMIN);

        publishedEvent = new Event();
        publishedEvent.setId(10L);
        publishedEvent.setTitle("Hackathon 2026");
        publishedEvent.setStatus(EventStatus.PUBLISHED);
        publishedEvent.setOrganizer(organizerUser);

        team = new Team("Alpha Team", publishedEvent, leaderUser);
        team.setId(100L);
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

    // 15. eligible participant added -> success
    @Test
    void eligibleParticipantAdded_success() {
        setupSecurityContext("leader@example.com", "ROLE_USER");
        when(userRepository.findByEmail("leader@example.com")).thenReturn(Optional.of(leaderUser));
        when(teamRepository.findById(100L)).thenReturn(Optional.of(team));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidateUser));
        when(registrationRepository.existsByUserIdAndEventIdAndTypeAndStatusIn(
                eq(2L), eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()
        )).thenReturn(true);
        when(teamMemberRepository.existsByTeamIdAndUserId(100L, 2L)).thenReturn(false);
        when(teamRepository.existsByEventIdAndLeaderId(10L, 2L)).thenReturn(false);
        when(teamMemberRepository.existsByTeamEventIdAndUserId(10L, 2L)).thenReturn(false);
        when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(inv -> inv.getArgument(0));

        TeamMemberRequest request = new TeamMemberRequest(100L, 2L);
        TeamMember created = teamMemberService.createTeamMember(request);

        assertNotNull(created);
        assertEquals(team, created.getTeam());
        assertEquals(candidateUser, created.getUser());
        verify(teamMemberRepository).save(any(TeamMember.class));
    }

    // 16. volunteer-only user added -> 400
    @Test
    void volunteerOnlyUserAdded_throws400() {
        setupSecurityContext("leader@example.com", "ROLE_USER");
        when(userRepository.findByEmail("leader@example.com")).thenReturn(Optional.of(leaderUser));
        when(teamRepository.findById(100L)).thenReturn(Optional.of(team));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidateUser));
        // Returns false because registration is not PARTICIPANT
        when(registrationRepository.existsByUserIdAndEventIdAndTypeAndStatusIn(
                eq(2L), eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()
        )).thenReturn(false);

        TeamMemberRequest request = new TeamMemberRequest(100L, 2L);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamMemberService.createTeamMember(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("active participant registration"));
    }

    // 17. user without event participant registration -> 400
    @Test
    void userWithoutEventParticipantRegistration_throws400() {
        setupSecurityContext("leader@example.com", "ROLE_USER");
        when(userRepository.findByEmail("leader@example.com")).thenReturn(Optional.of(leaderUser));
        when(teamRepository.findById(100L)).thenReturn(Optional.of(team));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidateUser));
        when(registrationRepository.existsByUserIdAndEventIdAndTypeAndStatusIn(
                eq(2L), eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()
        )).thenReturn(false);

        TeamMemberRequest request = new TeamMemberRequest(100L, 2L);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamMemberService.createTeamMember(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    // 18. duplicate member in same team -> 409
    @Test
    void duplicateMemberInSameTeam_throws409() {
        setupSecurityContext("leader@example.com", "ROLE_USER");
        when(userRepository.findByEmail("leader@example.com")).thenReturn(Optional.of(leaderUser));
        when(teamRepository.findById(100L)).thenReturn(Optional.of(team));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidateUser));
        when(registrationRepository.existsByUserIdAndEventIdAndTypeAndStatusIn(
                eq(2L), eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()
        )).thenReturn(true);
        when(teamMemberRepository.existsByTeamIdAndUserId(100L, 2L)).thenReturn(true);

        TeamMemberRequest request = new TeamMemberRequest(100L, 2L);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamMemberService.createTeamMember(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("already a member"));
    }

    // 19. user already in another team for same event -> 409
    @Test
    void userAlreadyInAnotherTeamForSameEvent_throws409() {
        setupSecurityContext("leader@example.com", "ROLE_USER");
        when(userRepository.findByEmail("leader@example.com")).thenReturn(Optional.of(leaderUser));
        when(teamRepository.findById(100L)).thenReturn(Optional.of(team));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidateUser));
        when(registrationRepository.existsByUserIdAndEventIdAndTypeAndStatusIn(
                eq(2L), eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()
        )).thenReturn(true);
        when(teamMemberRepository.existsByTeamIdAndUserId(100L, 2L)).thenReturn(false);
        when(teamRepository.existsByEventIdAndLeaderId(10L, 2L)).thenReturn(false);
        when(teamMemberRepository.existsByTeamEventIdAndUserId(10L, 2L)).thenReturn(true);

        TeamMemberRequest request = new TeamMemberRequest(100L, 2L);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamMemberService.createTeamMember(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("another team"));
    }

    // 20. team leader duplicate membership prevented/handled correctly
    @Test
    void teamLeaderDuplicateMembership_preventedWithConflict() {
        setupSecurityContext("leader@example.com", "ROLE_USER");
        when(userRepository.findByEmail("leader@example.com")).thenReturn(Optional.of(leaderUser));
        when(teamRepository.findById(100L)).thenReturn(Optional.of(team));
        when(userRepository.findById(1L)).thenReturn(Optional.of(leaderUser));
        when(registrationRepository.existsByUserIdAndEventIdAndTypeAndStatusIn(
                eq(1L), eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()
        )).thenReturn(true);

        // Attempting to add the leader (userId=1L) as a team member
        TeamMemberRequest request = new TeamMemberRequest(100L, 1L);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamMemberService.createTeamMember(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("team leader is already"));
    }

    // 21. USER leader adds member -> success
    @Test
    void userLeaderAddsMember_success() {
        setupSecurityContext("leader@example.com", "ROLE_USER");
        when(userRepository.findByEmail("leader@example.com")).thenReturn(Optional.of(leaderUser));
        when(teamRepository.findById(100L)).thenReturn(Optional.of(team));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidateUser));
        when(registrationRepository.existsByUserIdAndEventIdAndTypeAndStatusIn(
                eq(2L), eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()
        )).thenReturn(true);
        when(teamMemberRepository.existsByTeamIdAndUserId(100L, 2L)).thenReturn(false);
        when(teamRepository.existsByEventIdAndLeaderId(10L, 2L)).thenReturn(false);
        when(teamMemberRepository.existsByTeamEventIdAndUserId(10L, 2L)).thenReturn(false);
        when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(inv -> inv.getArgument(0));

        TeamMemberRequest request = new TeamMemberRequest(100L, 2L);
        TeamMember created = teamMemberService.createTeamMember(request);

        assertNotNull(created);
        assertEquals(candidateUser, created.getUser());
    }

    // 22. USER who is not leader attempts add -> 403
    @Test
    void userWhoIsNotLeaderAttemptsAdd_throws403() {
        setupSecurityContext("other@example.com", "ROLE_USER");
        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(otherUser));
        when(teamRepository.findById(100L)).thenReturn(Optional.of(team));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidateUser));

        TeamMemberRequest request = new TeamMemberRequest(100L, 2L);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamMemberService.createTeamMember(request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Only the team leader"));
    }

    // 23. ORGANIZER adds member to own event team -> success
    @Test
    void organizerAddsMemberToOwnEventTeam_success() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerUser));
        when(teamRepository.findById(100L)).thenReturn(Optional.of(team));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidateUser));
        when(registrationRepository.existsByUserIdAndEventIdAndTypeAndStatusIn(
                eq(2L), eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()
        )).thenReturn(true);
        when(teamMemberRepository.existsByTeamIdAndUserId(100L, 2L)).thenReturn(false);
        when(teamRepository.existsByEventIdAndLeaderId(10L, 2L)).thenReturn(false);
        when(teamMemberRepository.existsByTeamEventIdAndUserId(10L, 2L)).thenReturn(false);
        when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(inv -> inv.getArgument(0));

        TeamMemberRequest request = new TeamMemberRequest(100L, 2L);
        TeamMember created = teamMemberService.createTeamMember(request);

        assertNotNull(created);
        assertEquals(candidateUser, created.getUser());
    }

    // 24. ORGANIZER modifies another organizer's team -> 403
    @Test
    void organizerModifiesAnotherOrganizersTeam_throws403() {
        setupSecurityContext("organizer@example.com", "ROLE_ORGANIZER");
        when(userRepository.findByEmail("organizer@example.com")).thenReturn(Optional.of(organizerUser));

        User foreignOrganizer = new User();
        foreignOrganizer.setId(99L);
        foreignOrganizer.setEmail("foreign@example.com");

        Event foreignEvent = new Event();
        foreignEvent.setId(50L);
        foreignEvent.setStatus(EventStatus.PUBLISHED);
        foreignEvent.setOrganizer(foreignOrganizer);

        Team foreignTeam = new Team("Foreign Team", foreignEvent, leaderUser);
        foreignTeam.setId(200L);

        when(teamRepository.findById(200L)).thenReturn(Optional.of(foreignTeam));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidateUser));

        TeamMemberRequest request = new TeamMemberRequest(200L, 2L);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamMemberService.createTeamMember(request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    // 25. ADMIN adds/removes member -> success
    @Test
    void adminAddsAndRemovesMember_success() {
        setupSecurityContext("admin@example.com", "ROLE_ADMIN");
        when(teamRepository.findById(100L)).thenReturn(Optional.of(team));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidateUser));
        when(registrationRepository.existsByUserIdAndEventIdAndTypeAndStatusIn(
                eq(2L), eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()
        )).thenReturn(true);
        when(teamMemberRepository.existsByTeamIdAndUserId(100L, 2L)).thenReturn(false);
        when(teamRepository.existsByEventIdAndLeaderId(10L, 2L)).thenReturn(false);
        when(teamMemberRepository.existsByTeamEventIdAndUserId(10L, 2L)).thenReturn(false);
        when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(inv -> inv.getArgument(0));

        TeamMemberRequest request = new TeamMemberRequest(100L, 2L);
        TeamMember created = teamMemberService.createTeamMember(request);
        assertNotNull(created);

        // ADMIN removes member
        TeamMember memberToRemove = new TeamMember(team, candidateUser);
        memberToRemove.setId(500L);
        when(teamMemberRepository.findById(500L)).thenReturn(Optional.of(memberToRemove));

        teamMemberService.deleteTeamMember(500L);
        verify(teamMemberRepository).delete(memberToRemove);
    }

    // 26. team member removed successfully
    @Test
    void teamMemberRemovedSuccessfully() {
        setupSecurityContext("leader@example.com", "ROLE_USER");
        when(userRepository.findByEmail("leader@example.com")).thenReturn(Optional.of(leaderUser));

        TeamMember member = new TeamMember(team, candidateUser);
        member.setId(501L);
        when(teamMemberRepository.findById(501L)).thenReturn(Optional.of(member));

        teamMemberService.deleteTeamMember(501L);
        verify(teamMemberRepository).delete(member);
        verifyNoInteractions(registrationRepository);
    }

    // 27. removing leader is prevented if it would invalidate the team
    @Test
    void removingLeaderIsPrevented_throws400() {
        // Suppose a team member row had the leader user
        TeamMember leaderAsMember = new TeamMember(team, leaderUser);
        leaderAsMember.setId(502L);
        when(teamMemberRepository.findById(502L)).thenReturn(Optional.of(leaderAsMember));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamMemberService.deleteTeamMember(502L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Cannot remove the team leader"));
    }

    // Attempting to add member to COMPLETED or CANCELLED event -> 400
    @Test
    void addingMemberToCompletedEvent_throws400() {
        Event completed = new Event();
        completed.setId(11L);
        completed.setStatus(EventStatus.COMPLETED);

        Team completedTeam = new Team("Done", completed, leaderUser);
        completedTeam.setId(105L);

        when(teamRepository.findById(105L)).thenReturn(Optional.of(completedTeam));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidateUser));

        TeamMemberRequest request = new TeamMemberRequest(105L, 2L);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamMemberService.createTeamMember(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("COMPLETED"));
    }

    // Candidate already leading another team in event -> 409
    @Test
    void candidateAlreadyLeadingAnotherTeamInEvent_throws409() {
        setupSecurityContext("leader@example.com", "ROLE_USER");
        when(userRepository.findByEmail("leader@example.com")).thenReturn(Optional.of(leaderUser));
        when(teamRepository.findById(100L)).thenReturn(Optional.of(team));
        when(userRepository.findById(2L)).thenReturn(Optional.of(candidateUser));
        when(registrationRepository.existsByUserIdAndEventIdAndTypeAndStatusIn(
                eq(2L), eq(10L), eq(RegistrationType.PARTICIPANT), anyCollection()
        )).thenReturn(true);
        when(teamMemberRepository.existsByTeamIdAndUserId(100L, 2L)).thenReturn(false);
        when(teamRepository.existsByEventIdAndLeaderId(10L, 2L)).thenReturn(true);

        TeamMemberRequest request = new TeamMemberRequest(100L, 2L);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> teamMemberService.createTeamMember(request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("already leading another team"));
    }
}
