package com.eventease.controller;

import com.eventease.dto.request.TeamMemberRequest;
import com.eventease.dto.response.TeamMemberResponse;
import com.eventease.entity.TeamMember;
import com.eventease.service.TeamMemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/team-members")
public class TeamMemberController {

    private final TeamMemberService teamMemberService;

    public TeamMemberController(TeamMemberService teamMemberService) {
        this.teamMemberService = teamMemberService;
    }

    @PostMapping
    public ResponseEntity<TeamMemberResponse> createTeamMember(@Valid @RequestBody TeamMemberRequest request) {
        TeamMember created = teamMemberService.createTeamMember(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeamMemberResponse> getTeamMemberById(@PathVariable Long id) {
        TeamMember member = teamMemberService.getTeamMemberById(id);
        return ResponseEntity.ok(toResponse(member));
    }

    @GetMapping
    public ResponseEntity<List<TeamMemberResponse>> getAllTeamMembers() {
        List<TeamMemberResponse> responses = teamMemberService.getAllTeamMembers()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/team/{teamId}")
    public ResponseEntity<List<TeamMemberResponse>> getTeamMembersByTeam(@PathVariable Long teamId) {
        List<TeamMemberResponse> responses = teamMemberService.getTeamMembersByTeam(teamId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TeamMemberResponse> updateTeamMember(@PathVariable Long id,
                                                               @Valid @RequestBody TeamMemberRequest request) {
        TeamMember updated = teamMemberService.updateTeamMember(id, request);
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTeamMember(@PathVariable Long id) {
        teamMemberService.deleteTeamMember(id);
        return ResponseEntity.noContent().build();
    }

    private TeamMemberResponse toResponse(TeamMember member) {
        Long teamId = member.getTeam() != null ? member.getTeam().getId() : null;
        String teamName = member.getTeam() != null ? member.getTeam().getName() : null;
        Long userId = member.getUser() != null ? member.getUser().getId() : null;
        String userName = member.getUser() != null ? member.getUser().getName() : null;
        return new TeamMemberResponse(
                member.getId(),
                teamId,
                teamName,
                userId,
                userName,
                member.getJoinedAt()
        );
    }
}
