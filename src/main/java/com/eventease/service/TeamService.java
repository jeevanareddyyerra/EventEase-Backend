package com.eventease.service;

import com.eventease.dto.request.TeamRequest;
import com.eventease.entity.Event;
import com.eventease.entity.EventStatus;
import com.eventease.entity.RegistrationStatus;
import com.eventease.entity.RegistrationType;
import com.eventease.entity.Team;
import com.eventease.entity.TeamMember;
import com.eventease.entity.User;
import com.eventease.exception.BusinessRuleException;
import com.eventease.exception.ResourceNotFoundException;
import com.eventease.repository.EventRepository;
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
public class TeamService {

    private static final List<RegistrationStatus> ACTIVE_PARTICIPANT_STATUSES = List.of(
            RegistrationStatus.REGISTERED,
            RegistrationStatus.ACCEPTED
    );

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final RegistrationRepository registrationRepository;

    public TeamService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            EventRepository eventRepository,
            UserRepository userRepository,
            RegistrationRepository registrationRepository
    ) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.registrationRepository = registrationRepository;
    }

    @Transactional
    public Team createTeam(TeamRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Team request cannot be null.", HttpStatus.BAD_REQUEST);
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new BusinessRuleException("Team name cannot be blank.", HttpStatus.BAD_REQUEST);
        }
        if (request.getEventId() == null) {
            throw new BusinessRuleException("Event ID is required.", HttpStatus.BAD_REQUEST);
        }

        // 1. EVENT EXISTENCE
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + request.getEventId()));

        // 2. EVENT STATUS (not COMPLETED or CANCELLED)
        if (event.getStatus() == EventStatus.COMPLETED || event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleException("Cannot create a team for a " + event.getStatus() + " event.", HttpStatus.BAD_REQUEST);
        }

        // 3. USER RESOLUTION & AUTHORIZATION FOR LEADER
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User authUser = getAuthenticatedUser();

        User leader;
        if (auth != null && hasRole(auth, "ADMIN")) {
            // ADMIN can specify leaderId
            if (request.getLeaderId() == null) {
                throw new BusinessRuleException("Leader ID is required.", HttpStatus.BAD_REQUEST);
            }
            leader = userRepository.findById(request.getLeaderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Leader not found with id: " + request.getLeaderId()));
        } else if (auth != null && hasRole(auth, "ORGANIZER")) {
            // ORGANIZER can create teams for events they own
            if (authUser != null && event.getOrganizer() != null && !isOrganizerOwner(event.getOrganizer(), authUser)) {
                throw new BusinessRuleException("Organizers can only create teams for their own events.", HttpStatus.FORBIDDEN);
            }
            if (request.getLeaderId() == null) {
                throw new BusinessRuleException("Leader ID is required.", HttpStatus.BAD_REQUEST);
            }
            leader = userRepository.findById(request.getLeaderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Leader not found with id: " + request.getLeaderId()));
        } else {
            // Normal USER (or no elevated role):
            if (authUser != null) {
                // USER can create a team only for themselves as the team leader.
                // Ignore any spoofed leaderId supplied in the request.
                leader = authUser;
            } else {
                // Fallback when no SecurityContext is present (e.g. testing)
                if (request.getLeaderId() == null) {
                    throw new BusinessRuleException("Leader ID is required.", HttpStatus.BAD_REQUEST);
                }
                leader = userRepository.findById(request.getLeaderId())
                        .orElseThrow(() -> new ResourceNotFoundException("Leader not found with id: " + request.getLeaderId()));
            }
        }

        // 4. DUPLICATE TEAM NAME IN SAME EVENT
        if (teamRepository.existsByEventIdAndName(event.getId(), request.getName().trim())) {
            throw new BusinessRuleException("A team with this name already exists for this event.", HttpStatus.CONFLICT);
        }

        // 5. LEADER ACTIVE PARTICIPANT REGISTRATION CHECK
        boolean hasActiveParticipantRegistration = registrationRepository.existsByUserIdAndEventIdAndTypeAndStatusIn(
                leader.getId(),
                event.getId(),
                RegistrationType.PARTICIPANT,
                ACTIVE_PARTICIPANT_STATUSES
        );
        if (!hasActiveParticipantRegistration) {
            throw new BusinessRuleException("Team leader must have an active participant registration for this event.", HttpStatus.BAD_REQUEST);
        }

        // 6. MULTIPLE TEAMS MEMBERSHIP / LEADERSHIP IN SAME EVENT CHECK
        if (teamRepository.existsByEventIdAndLeaderId(event.getId(), leader.getId())) {
            throw new BusinessRuleException("User is already leading a team for this event.", HttpStatus.CONFLICT);
        }
        if (teamMemberRepository.existsByTeamEventIdAndUserId(event.getId(), leader.getId())) {
            throw new BusinessRuleException("User is already a member of a team for this event.", HttpStatus.CONFLICT);
        }

        Team team = new Team();
        team.setName(request.getName().trim());
        team.setEvent(event);
        team.setLeader(leader);
        return teamRepository.save(team);
    }

    @Transactional
    public Team createTeam(Team team) {
        if (team == null) {
            throw new BusinessRuleException("Team cannot be null.", HttpStatus.BAD_REQUEST);
        }
        Long eventId = team.getEvent() != null ? team.getEvent().getId() : null;
        Long leaderId = team.getLeader() != null ? team.getLeader().getId() : null;
        return createTeam(new TeamRequest(team.getName(), eventId, leaderId));
    }

    public Team getTeamById(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + id));
    }

    public List<Team> getAllTeams() {
        return teamRepository.findAll();
    }

    public List<Team> getTeamsByEvent(Long eventId) {
        return teamRepository.findByEventId(eventId);
    }

    @Transactional
    public Team updateTeam(Long id, TeamRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Team request cannot be null.", HttpStatus.BAD_REQUEST);
        }
        Team existing = getTeamById(id);

        if (existing.getEvent().getStatus() == EventStatus.COMPLETED || existing.getEvent().getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleException("Cannot update a team for a " + existing.getEvent().getStatus() + " event.", HttpStatus.BAD_REQUEST);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User authUser = getAuthenticatedUser();

        if (auth != null && hasRole(auth, "ADMIN")) {
            // ADMIN allowed
        } else if (auth != null && hasRole(auth, "ORGANIZER")) {
            if (authUser != null && existing.getEvent().getOrganizer() != null && !isOrganizerOwner(existing.getEvent().getOrganizer(), authUser)) {
                throw new BusinessRuleException("Organizers can only manage teams for their own events.", HttpStatus.FORBIDDEN);
            }
        } else if (auth != null && (hasRole(auth, "USER") || authUser != null)) {
            if (authUser != null && !isLeader(existing.getLeader(), authUser)) {
                throw new BusinessRuleException("Only the team leader can update this team.", HttpStatus.FORBIDDEN);
            }
        }

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            String newName = request.getName().trim();
            if (!newName.equalsIgnoreCase(existing.getName()) && teamRepository.existsByEventIdAndName(existing.getEvent().getId(), newName)) {
                throw new BusinessRuleException("A team with this name already exists for this event.", HttpStatus.CONFLICT);
            }
            existing.setName(newName);
        }

        return teamRepository.save(existing);
    }

    @Transactional
    public Team updateTeam(Long id, Team team) {
        if (team == null) {
            throw new BusinessRuleException("Team cannot be null.", HttpStatus.BAD_REQUEST);
        }
        Long eventId = team.getEvent() != null ? team.getEvent().getId() : null;
        Long leaderId = team.getLeader() != null ? team.getLeader().getId() : null;
        return updateTeam(id, new TeamRequest(team.getName(), eventId, leaderId));
    }

    @Transactional
    public void deleteTeam(Long id) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + id));

        // Event must not be COMPLETED or CANCELLED
        if (team.getEvent().getStatus() == EventStatus.COMPLETED || team.getEvent().getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleException("Cannot delete a team for a " + team.getEvent().getStatus() + " event.", HttpStatus.BAD_REQUEST);
        }

        // Authorization
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User authUser = getAuthenticatedUser();

        if (auth != null && hasRole(auth, "ADMIN")) {
            // ADMIN can delete any team
        } else if (auth != null && hasRole(auth, "ORGANIZER")) {
            if (authUser != null && team.getEvent().getOrganizer() != null && !isOrganizerOwner(team.getEvent().getOrganizer(), authUser)) {
                throw new BusinessRuleException("Organizers can only delete teams for their own events.", HttpStatus.FORBIDDEN);
            }
        } else if (auth != null && (hasRole(auth, "USER") || authUser != null)) {
            if (authUser != null && !isLeader(team.getLeader(), authUser)) {
                throw new BusinessRuleException("Only the team leader can delete this team.", HttpStatus.FORBIDDEN);
            }
        }

        // Safely and transactionally remove associated team members
        List<TeamMember> members = teamMemberRepository.findByTeamId(team.getId());
        teamMemberRepository.deleteAll(members);

        teamRepository.delete(team);
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
