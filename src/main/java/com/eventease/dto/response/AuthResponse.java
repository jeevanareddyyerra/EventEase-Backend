package com.eventease.dto.response;

import com.eventease.entity.OrganizerStatus;
import com.eventease.entity.Role;

public class AuthResponse {

    private String token;
    private Long userId;
    private String name;
    private String email;
    private Role role;
    private OrganizerStatus organizerStatus;

    public AuthResponse() {
    }

    public AuthResponse(String token, Long userId, String name, String email, Role role) {
        this(token, userId, name, email, role, OrganizerStatus.NONE);
    }

    public AuthResponse(String token, Long userId, String name, String email, Role role, OrganizerStatus organizerStatus) {
        this.token = token;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = role;
        this.organizerStatus = organizerStatus != null ? organizerStatus : OrganizerStatus.NONE;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public OrganizerStatus getOrganizerStatus() {
        return organizerStatus != null ? organizerStatus : OrganizerStatus.NONE;
    }

    public void setOrganizerStatus(OrganizerStatus organizerStatus) {
        this.organizerStatus = organizerStatus != null ? organizerStatus : OrganizerStatus.NONE;
    }
}
