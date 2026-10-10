package com.eventease.controller;

import com.eventease.dto.request.TeamRequest;
import com.eventease.dto.response.TeamResponse;
import com.eventease.entity.Team;
import com.eventease.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    public ResponseEntity<TeamResponse> createTeam(@Valid @RequestBody TeamRequest request) {
        Team created = teamService.createTeam(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeamResponse> getTeamById(@PathVariable Long id) {
        Team team = teamService.getTeamById(id);
        return ResponseEntity.ok(toResponse(team));
    }

    @GetMapping
    public ResponseEntity<List<TeamResponse>> getAllTeams() {
        List<TeamResponse> responses = teamService.getAllTeams()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<List<TeamResponse>> getTeamsByEvent(@PathVariable Long eventId) {
        List<TeamResponse> responses = teamService.getTeamsByEvent(eventId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TeamResponse> updateTeam(@PathVariable Long id, @Valid @RequestBody TeamRequest request) {
        Team updated = teamService.updateTeam(id, request);
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTeam(@PathVariable Long id) {
        teamService.deleteTeam(id);
        return ResponseEntity.noContent().build();
    }

    private TeamResponse toResponse(Team team) {
        Long eventId = team.getEvent() != null ? team.getEvent().getId() : null;
        Long leaderId = team.getLeader() != null ? team.getLeader().getId() : null;
        String leaderName = team.getLeader() != null ? team.getLeader().getName() : null;
        return new TeamResponse(
                team.getId(),
                team.getName(),
                eventId,
                leaderId,
                leaderName
        );
    }
}
