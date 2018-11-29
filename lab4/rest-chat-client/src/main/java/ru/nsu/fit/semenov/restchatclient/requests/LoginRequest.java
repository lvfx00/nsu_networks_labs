package ru.nsu.fit.semenov.restchatclient.requests;

public class LoginRequest {
    private final String username;

    public LoginRequest(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}

