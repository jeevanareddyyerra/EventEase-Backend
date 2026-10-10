package com.eventease.service;

import com.eventease.dto.request.TeamMemberRequest;
import com.eventease.entity.Event;
import com.eventease.entity.EventStatus;
import com.eventease.entity.RegistrationStatus;
import com.eventease.entity.RegistrationType;
import com.eventease.entity.Team;
import com.eventease.entity.TeamMember;
import com.eventease.entity.User;
import com.eventease.exception.BusinessRuleException;
import com.eventease.exception.ResourceNotFoundException;
import com.eventease.repository.RegistrationRepository;
import com.eventease.repository.TeamMemberRepository;
import com.eventease.repository.TeamRepository;
import com.eventease.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TeamMemberService {

    private static final List<RegistrationStatus> ACTIVE_PARTICIPANT_STATUSES = List.of(
            RegistrationStatus.REGISTERED,
            RegistrationStatus.ACCEPTED
    );

    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final RegistrationRepository registrationRepository;

    public TeamMemberService(
            TeamMemberRepository teamMemberRepository,
            TeamRepository teamRepository,
            UserRepository userRepository,
            RegistrationRepository registrationRepository
    ) {
        this.teamMemberRepository = teamMemberRepository;
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.registrationRepository = registrationRepository;
    }

    @Transactional
    public TeamMember createTeamMember(TeamMemberRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Team member request cannot be null.", HttpStatus.BAD_REQUEST);
        }
        if (request.getTeamId() == null) {
            throw new BusinessRuleException("Team ID is required.", HttpStatus.BAD_REQUEST);
        }
        if (request.getUserId() == null) {
            throw new BusinessRuleException("User ID is required.", HttpStatus.BAD_REQUEST);
        }

        // 1. TEAM EXISTENCE
        Team team = teamRepository.findById(request.getTeamId())
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + request.getTeamId()));

        // 2. USER EXISTENCE
        User candidateUser = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getUserId()));

        // 3. EVENT EXISTENCE & STATUS
        Event event = team.getEvent();
        if (event == null) {
            throw new ResourceNotFoundException("Event not found for team with id: " + team.getId());
        }
        if (event.getStatus() == EventStatus.COMPLETED || event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleException("Cannot add members to a team for a " + event.getStatus() + " event.", HttpStatus.BAD_REQUEST);
        }

        // 4. AUTHORIZATION
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User authUser = getAuthenticatedUser();

        if (auth != null && hasRole(auth, "ADMIN")) {
            // ADMIN allowed
        } else if (auth != null && hasRole(auth, "ORGANIZER")) {
            if (authUser != null && event.getOrganizer() != null && !isOrganizerOwner(event.getOrganizer(), authUser)) {
                throw new BusinessRuleException("Organizers can only manage teams for their own events.", HttpStatus.FORBIDDEN);
            }
        } else if (auth != null && (hasRole(auth, "USER") || authUser != null)) {
            if (authUser != null && !isLeader(team.getLeader(), authUser)) {
                throw new BusinessRuleException("Only the team leader can manage team members.", HttpStatus.FORBIDDEN);
            }
        }

        // 5. ACTIVE PARTICIPANT REGISTRATION CHECK
        boolean hasActiveParticipantRegistration = registrationRepository.existsByUserIdAndEventIdAndTypeAndStatusIn(
                candidateUser.getId(),
                event.getId(),
                RegistrationType.PARTICIPANT,
                ACTIVE_PARTICIPANT_STATUSES
        );
        if (!hasActiveParticipantRegistration) {
            throw new BusinessRuleException("User must have an active participant registration for this event.", HttpStatus.BAD_REQUEST);
        }

        // 6. MEMBERSHIP UNIQUENESS / DUPLICATE CHECKS
        // A. Candidate is already the leader of this team
        if (team.getLeader() != null && team.getLeader().getId().equals(candidateUser.getId())) {
            throw new BusinessRuleException("The team leader is already part of this team.", HttpStatus.CONFLICT);
        }

        // B. Candidate is already a member of this team
        if (teamMemberRepository.existsByTeamIdAndUserId(team.getId(), candidateUser.getId())) {
            throw new BusinessRuleException("User is already a member of this team.", HttpStatus.CONFLICT);
        }

        // C. Candidate is already the leader of another team in this event
        if (teamRepository.existsByEventIdAndLeaderId(event.getId(), candidateUser.getId())) {
            throw new BusinessRuleException("User is already leading another team for this event.", HttpStatus.CONFLICT);
        }

        // D. Candidate is already a member of another team in this event
        if (teamMemberRepository.existsByTeamEventIdAndUserId(event.getId(), candidateUser.getId())) {
            throw new BusinessRuleException("User is already a member of another team for this event.", HttpStatus.CONFLICT);
        }

        TeamMember member = new TeamMember(team, candidateUser);
        return teamMemberRepository.save(member);
    }

    @Transactional
    public TeamMember createTeamMember(TeamMember teamMember) {
        if (teamMember == null) {
            throw new BusinessRuleException("TeamMember cannot be null.", HttpStatus.BAD_REQUEST);
        }
        Long teamId = teamMember.getTeam() != null ? teamMember.getTeam().getId() : null;
        Long userId = teamMember.getUser() != null ? teamMember.getUser().getId() : null;
        return createTeamMember(new TeamMemberRequest(teamId, userId));
    }

    public TeamMember getTeamMemberById(Long id) {
        return teamMemberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TeamMember not found with id: " + id));
    }

    public List<TeamMember> getAllTeamMembers() {
        return teamMemberRepository.findAll();
    }

    public List<TeamMember> getTeamMembersByTeam(Long teamId) {
        return teamMemberRepository.findByTeamId(teamId);
    }

    @Transactional
    public TeamMember updateTeamMember(Long id, TeamMemberRequest request) {
        TeamMember existing = getTeamMemberById(id);
        return existing;
    }

    @Transactional
    public TeamMember updateTeamMember(Long id, TeamMember teamMember) {
        TeamMember existing = getTeamMemberById(id);
        if (teamMember.getTeam() != null) {
            existing.setTeam(teamMember.getTeam());
        }
        if (teamMember.getUser() != null) {
            existing.setUser(teamMember.getUser());
        }
        return teamMemberRepository.save(existing);
    }

    @Transactional
    public void deleteTeamMember(Long id) {
        TeamMember member = teamMemberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TeamMember not found with id: " + id));

        Team team = member.getTeam();
        Event event = team != null ? team.getEvent() : null;

        if (event != null && (event.getStatus() == EventStatus.COMPLETED || event.getStatus() == EventStatus.CANCELLED)) {
            throw new BusinessRuleException("Cannot remove team members for a " + event.getStatus() + " event.", HttpStatus.BAD_REQUEST);
        }

        // Prevent removing the team leader if somehow represented as a team member
        if (team != null && team.getLeader() != null && team.getLeader().getId().equals(member.getUser().getId())) {
            throw new BusinessRuleException("Cannot remove the team leader from the team.", HttpStatus.BAD_REQUEST);
        }

        // Authorization
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User authUser = getAuthenticatedUser();

        if (auth != null && hasRole(auth, "ADMIN")) {
            // ADMIN allowed
        } else if (auth != null && hasRole(auth, "ORGANIZER")) {
            if (authUser != null && event != null && event.getOrganizer() != null && !isOrganizerOwner(event.getOrganizer(), authUser)) {
                throw new BusinessRuleException("Organizers can only manage teams for their own events.", HttpStatus.FORBIDDEN);
            }
        } else if (auth != null && (hasRole(auth, "USER") || authUser != null)) {
            if (authUser != null && team != null && !isLeader(team.getLeader(), authUser)) {
                throw new BusinessRuleException("Only the team leader can remove team members.", HttpStatus.FORBIDDEN);
            }
        }

        teamMemberRepository.delete(member);
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

    private boolean isLeader(User leader, User authUser) {
        if (leader == null || authUser == null) return false;
        if (leader.getId() != null && authUser.getId() != null && leader.getId().equals(authUser.getId())) {
            return true;
        }
        return leader.getEmail() != null && leader.getEmail().equalsIgnoreCase(authUser.getEmail());
    }
}
