package com.eventease.dto.request;

public class AuthRequest extends LoginRequest {

    public AuthRequest() {
        super();
    }

    public AuthRequest(String email, String password) {
        super(email, password);
    }
}
