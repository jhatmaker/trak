package com.trackmyraces.trak.data.network.dto;

public class AuthRequest {
    public String email;
    public String password;

    public AuthRequest(String email, String password) {
        this.email    = email;
        this.password = password;
    }
}
