package com.eventease.dto.response;

import java.time.LocalDateTime;

public class TeamMemberResponse {

    private Long id;
    private Long teamId;
    private String teamName;
    private Long userId;
    private String userName;
    private LocalDateTime joinedAt;

    public TeamMemberResponse() {
    }

    public TeamMemberResponse(Long id, Long teamId, String teamName, Long userId, String userName, LocalDateTime joinedAt) {
        this.id = id;
        this.teamId = teamId;
        this.teamName = teamName;
        this.userId = userId;
        this.userName = userName;
        this.joinedAt = joinedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
}
