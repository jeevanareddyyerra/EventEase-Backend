package com.eventease.dto.request;

import jakarta.validation.constraints.NotNull;

public class TeamMemberRequest {

    @NotNull(message = "Team ID is required")
    private Long teamId;

    @NotNull(message = "User ID is required")
    private Long userId;

    public TeamMemberRequest() {
    }

    public TeamMemberRequest(Long teamId, Long userId) {
        this.teamId = teamId;
        this.userId = userId;
    }

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
