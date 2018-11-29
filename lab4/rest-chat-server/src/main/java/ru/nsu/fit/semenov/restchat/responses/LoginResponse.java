package ru.nsu.fit.semenov.restchat.responses;

public class LoginResponse {
    private final int id;
    private final String username;
    private final boolean online;
    private final String token;

    public LoginResponse(int id, String username, boolean online, String token) {
        this.id = id;
        this.username = username;
        this.online = online;
        this.token = token;
    }

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public boolean isOnline() {
        return online;
    }

    public String getToken() {
        return token;
    }
}
