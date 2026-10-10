package com.eventease.dto.response;

public class TeamResponse {

    private Long id;
    private String name;
    private Long eventId;
    private Long leaderId;
    private String leaderName;

    public TeamResponse() {
    }

    public TeamResponse(Long id, String name, Long eventId, Long leaderId, String leaderName) {
        this.id = id;
        this.name = name;
        this.eventId = eventId;
        this.leaderId = leaderId;
        this.leaderName = leaderName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Long getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(Long leaderId) {
        this.leaderId = leaderId;
    }

    public String getLeaderName() {
        return leaderName;
    }

    public void setLeaderName(String leaderName) {
        this.leaderName = leaderName;
    }
}
